import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive_ce/hive_ce.dart';
import 'package:iceibank_mobile/api/api_cliente.dart';
import 'package:iceibank_mobile/dados/armazenamento.dart';
import 'package:iceibank_mobile/estado/banco.dart';
import 'package:iceibank_mobile/estado/gatilho_sincronizacao.dart';
import 'package:iceibank_mobile/modelo/malha.dart';

import 'agencia_falsa.dart';

void main() {
  late AgenciaFalsa agencia;
  late Banco banco;
  late StreamController<List<ConnectivityResult>> rede;

  setUp(() async {
    agencia = AgenciaFalsa();
    banco = Banco(
      ApiCliente(const Malha(host: 'h'), cliente: agencia.cliente),
      await Armazenamento.emMemoria(),
    );
    await banco.entrar(0, 'ana123');
    rede = StreamController();
  });

  tearDown(Hive.close);

  test(
    'a rede voltou (sai do modo aviao): sincroniza sem ninguem apertar nada',
    () async {
      final gatilho = GatilhoDeSincronizacao(
        banco,
        conectividade: rede.stream,
        intervalo: const Duration(hours: 1),
      )..iniciar();
      agencia.online = false;
      await banco.depositar(5000);

      agencia.online = true;
      rede.add([ConnectivityResult.wifi]);
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(banco.pendentes, isEmpty);
      expect(banco.conta!.saldoCentavos, 15000);
      gatilho.parar();
    },
  );

  test(
    'aparelho com rede mas agencia fora: o timer tenta de novo ate ela voltar',
    () async {
      final gatilho = GatilhoDeSincronizacao(
        banco,
        conectividade: rede.stream,
        intervalo: const Duration(milliseconds: 20),
      )..iniciar();
      agencia.online = false;
      await banco.depositar(5000);
      await Future<void>.delayed(const Duration(milliseconds: 60));
      expect(banco.pendentes, hasLength(1));

      agencia.online = true; // nenhum evento de conectividade
      await Future<void>.delayed(const Duration(milliseconds: 80));

      expect(banco.pendentes, isEmpty);
      expect(banco.offline, isFalse);
      gatilho.parar();
    },
  );

  test('evento de rede "none" nao dispara nada', () async {
    final gatilho = GatilhoDeSincronizacao(
      banco,
      conectividade: rede.stream,
      intervalo: const Duration(hours: 1),
    )..iniciar();
    agencia.online = false;
    await banco.depositar(5000);
    agencia.online = true;

    rede.add([ConnectivityResult.none]);
    await Future<void>.delayed(const Duration(milliseconds: 50));

    expect(banco.pendentes, hasLength(1));
    gatilho.parar();
  });
}
