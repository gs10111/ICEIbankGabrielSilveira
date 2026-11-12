import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hive_ce/hive_ce.dart';
import 'package:iceibank_mobile/api/api_cliente.dart';
import 'package:iceibank_mobile/dados/armazenamento.dart';
import 'package:iceibank_mobile/estado/banco.dart';
import 'package:iceibank_mobile/modelo/malha.dart';
import 'package:iceibank_mobile/modelo/operacao_pendente.dart';

import 'agencia_falsa.dart';

void main() {
  late AgenciaFalsa agencia;
  late Armazenamento armazenamento;
  late Banco banco;

  Banco novoBanco() => Banco(
    ApiCliente(const Malha(host: 'h'), cliente: agencia.cliente),
    armazenamento,
  );

  setUp(() async {
    armazenamento = await Armazenamento.abrir(
      Directory.systemTemp.createTempSync('iceibank').path,
    );
    agencia = AgenciaFalsa();
    banco = novoBanco();
    await banco.entrar(0, 'ana123');
  });

  tearDown(() => Hive.deleteFromDisk());

  group('armazenamento local', () {
    test(
      'sem conexao, a tela de saldo continua mostrando o ultimo dado conhecido',
      () async {
        agencia.online = false;

        await banco.atualizar();

        expect(banco.offline, isTrue);
        expect(banco.conta!.saldoCentavos, 10000);
      },
    );

    test(
      'reabrir o app offline restaura sessao, saldo e fila do disco',
      () async {
        agencia.online = false;
        await banco.depositar(5000);

        final reaberto = novoBanco()..restaurar();

        expect(reaberto.logado, isTrue);
        expect(reaberto.conta!.saldoCentavos, 10000);
        expect(reaberto.pendentes, hasLength(1));
      },
    );
  });

  group('fila de pendentes e UI otimista', () {
    test(
      'deposito offline nao falha: vira pendente com o vetor que o app tinha',
      () async {
        agencia.online = false;

        final mensagem = await banco.depositar(5000);

        expect(mensagem, contains('pendente'));
        expect(banco.pendentes.single.vetorConhecido, [1, 0, 0]);
        expect(banco.pendentes.single.estado, EstadoPendente.pendente);
      },
    );

    test(
      'saldo provisorio ja reflete a operacao pendente; o confirmado nao muda',
      () async {
        agencia.online = false;
        await banco.depositar(5000);
        await banco.sacar(2000);

        expect(banco.saldoProvisorioCentavos, 13000);
        expect(banco.conta!.saldoCentavos, 10000);
      },
    );

    test(
      'saque offline acima do saldo provisorio e recusado ja no app',
      () async {
        agencia.online = false;

        await expectLater(
          banco.sacar(50000),
          throwsA(isA<OperacaoRecusadaNoApp>()),
        );
        expect(banco.pendentes, isEmpty);
      },
    );

    test('com fila nao vazia, operacao nova entra na fila mesmo online: a ordem e preservada', () async {
      agencia.online = false;
      await banco.depositar(1000);
      agencia.online = true;

      await banco.depositar(2000);

      expect(agencia.aplicadas.where((a) => a.startsWith('DEPOSITO')), isEmpty);
      expect(banco.pendentes, hasLength(2));
    });

    test(
      'transferencia offline e recusada com mensagem clara e NAO entra na fila',
      () async {
        agencia.online = false;

        await expectLater(
          banco.transferir(1, 1000),
          throwsA(
            isA<OperacaoRecusadaNoApp>().having(
              (e) => e.mensagem,
              'mensagem',
              contains('conexão'),
            ),
          ),
        );
        expect(banco.pendentes, isEmpty);
      },
    );
  });

  group('sincronizacao ao reconectar', () {
    test(
      'aplica a pendente, esvazia a fila e atualiza o saldo confirmado',
      () async {
        agencia.online = false;
        await banco.depositar(5000);
        agencia.online = true;

        await banco.sincronizarPendentes();

        expect(agencia.aplicadas, ['SYNC DEPOSITO 50.0']);
        expect(banco.pendentes, isEmpty);
        expect(banco.conta!.saldoCentavos, 15000);
        expect(banco.offline, isFalse);
      },
    );

    test('varias operacoes offline seguidas: cada sucesso passa o vetor novo para a proxima', () async {
      agencia.online = false;
      await banco.depositar(1000);
      await banco.sacar(500);
      await banco.depositar(200);
      agencia.online = true;

      await banco.sincronizarPendentes();

      expect(agencia.aplicadas, [
        'SYNC DEPOSITO 10.0',
        'SYNC SAQUE 5.0',
        'SYNC DEPOSITO 2.0',
      ]);
      expect(banco.pendentes, isEmpty);
      expect(banco.conta!.saldoCentavos, 10700);
    });

    test(
      'ainda sem conexao: a fila fica intacta para a proxima tentativa',
      () async {
        agencia.online = false;
        await banco.depositar(5000);

        await banco.sincronizarPendentes();

        expect(banco.pendentes.single.estado, EstadoPendente.pendente);
      },
    );
  });

  group('conflito (409)', () {
    test('conta mudou enquanto offline: operacao NAO aplicada, marcada como conflito, saldo real na tela', () async {
      agencia.online = false;
      await banco.depositar(5000);
      agencia.mudarPorOutroCaminho(30);
      agencia.online = true;

      await banco.sincronizarPendentes();

      final conflito = banco.pendentes.single;
      expect(conflito.estado, EstadoPendente.conflito);
      expect(conflito.mensagem, contains('Conflito'));
      expect(conflito.saldoRealCentavos, 13000);
      expect(banco.conta!.saldoCentavos, 13000);
      expect(agencia.aplicadas.where((a) => a.startsWith('SYNC')), isEmpty);
      expect(
        banco.saldoProvisorioCentavos,
        13000,
        reason: 'conflito nao entra no saldo provisorio',
      );
    });

    test(
      'a pessoa decide reenviar: vai com o vetor novo e e aplicada',
      () async {
        agencia.online = false;
        await banco.depositar(5000);
        agencia.mudarPorOutroCaminho(30);
        agencia.online = true;
        await banco.sincronizarPendentes();

        await banco.reenviar(banco.pendentes.single);

        expect(banco.pendentes, isEmpty);
        expect(banco.conta!.saldoCentavos, 18000);
      },
    );

    test('a pessoa decide descartar: some da fila e nada e aplicado', () async {
      agencia.online = false;
      await banco.depositar(5000);
      agencia.mudarPorOutroCaminho(30);
      agencia.online = true;
      await banco.sincronizarPendentes();

      await banco.descartar(banco.pendentes.single);

      expect(banco.pendentes, isEmpty);
      expect(agencia.aplicadas.where((a) => a.startsWith('SYNC')), isEmpty);
    });
  });

  test(
    '401 durante a sincronizacao: sessao cai, mas a fila continua guardada',
    () async {
      agencia.online = false;
      await banco.depositar(5000);
      agencia.online = true;
      agencia.tokenValido = false;

      await banco.sincronizarPendentes();

      expect(banco.logado, isFalse);
      expect(armazenamento.pendentes(0), hasLength(1));
    },
  );
}
