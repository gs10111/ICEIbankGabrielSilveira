import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:iceibank_mobile/api/api_cliente.dart';
import 'package:iceibank_mobile/estado/monitor_lideranca.dart';
import 'package:iceibank_mobile/modelo/malha.dart';

class Malha3 {
  final Set<int> fora = {};
  int? lider = 2;
  final Map<int, int?> liderPorAgencia = {};
  final List<int> consultadas = [];

  MockClient get cliente => MockClient((r) async {
    final agencia = r.url.port - Malha.portaBase;
    consultadas.add(agencia);
    if (fora.contains(agencia)) throw http.ClientException('recusada');
    final resposta = liderPorAgencia.containsKey(agencia)
        ? liderPorAgencia[agencia]
        : lider;
    return http.Response(
      jsonEncode({'idAgencia': agencia, 'liderAtual': resposta}),
      200,
    );
  });
}

void main() {
  test('mostra a lider que a agencia consultada informa', () async {
    final malha = Malha3();
    final monitor = MonitorLideranca(
      ApiCliente(const Malha(host: 'h'), cliente: malha.cliente),
    );

    await monitor.consultar();

    expect(monitor.lider, 2);
    expect(monitor.consultadaEm, 0);
  });

  test(
    'agencia consultada fora do ar: usa a proxima antes de desistir',
    () async {
      final malha = Malha3()..fora.addAll({0, 1});
      final monitor = MonitorLideranca(
        ApiCliente(const Malha(host: 'h'), cliente: malha.cliente),
      );

      await monitor.consultar();

      expect(malha.consultadas, containsAll([0, 1, 2]));
      expect(monitor.lider, 2);
      expect(monitor.consultadaEm, 2);
    },
  );

  test('failover: a lider cai e a proxima consulta ja mostra a nova, sem ninguem tocar na tela', () async {
    final malha = Malha3();
    final monitor = MonitorLideranca(
      ApiCliente(const Malha(host: 'h'), cliente: malha.cliente),
    );
    await monitor.consultar();
    expect(monitor.lider, 2);

    malha.fora.add(2);
    malha.lider = 1;
    await monitor.consultar();

    expect(monitor.lider, 1);
  });

  test(
    'fica com a ultima agencia que respondeu, mesmo que a 0 volte',
    () async {
      final malha = Malha3()..fora.add(0);
      final monitor = MonitorLideranca(
        ApiCliente(const Malha(host: 'h'), cliente: malha.cliente),
      );
      await monitor.consultar();
      malha.fora.clear();

      await monitor.consultar();

      expect(monitor.consultadaEm, 1);
    },
  );

  group('funcionalidade adicional: agencias ativas', () {
    test('mostra quem responde e quem esta fora, nao so a lider', () async {
      final malha = Malha3()..fora.add(1);
      final monitor = MonitorLideranca(
        ApiCliente(const Malha(host: 'h'), cliente: malha.cliente),
      );

      await monitor.consultar();

      expect(monitor.ativas, {0: true, 1: false, 2: true});
    });

    test(
      'agencias discordando (uma ainda nao soube da eleicao) e sinalizado',
      () async {
        final malha = Malha3()
          ..fora.add(2)
          ..lider = 1
          ..liderPorAgencia[0] = 2;
        final monitor = MonitorLideranca(
          ApiCliente(const Malha(host: 'h'), cliente: malha.cliente),
        );

        await monitor.consultar();

        expect(monitor.divergencia, isTrue);
      },
    );

    test(
      'na consulta seguinte, ja convergidas, a divergencia some sozinha',
      () async {
        final malha = Malha3()
          ..fora.add(2)
          ..lider = 1
          ..liderPorAgencia[0] = 2;
        final monitor = MonitorLideranca(
          ApiCliente(const Malha(host: 'h'), cliente: malha.cliente),
        );
        await monitor.consultar();

        malha.liderPorAgencia.clear();
        await monitor.consultar();

        expect(monitor.divergencia, isFalse);
        expect(monitor.lider, 1);
      },
    );
  });

  test('nenhuma responde: o painel diz isso em vez de mostrar uma lider velha como atual', () async {
    final malha = Malha3()..fora.addAll({0, 1, 2});
    final monitor = MonitorLideranca(
      ApiCliente(const Malha(host: 'h'), cliente: malha.cliente),
    );

    await monitor.consultar();

    expect(monitor.nenhumaRespondeu, isTrue);
  });

  test('lider null = eleicao em andamento', () async {
    final malha = Malha3()..lider = null;
    final monitor = MonitorLideranca(
      ApiCliente(const Malha(host: 'h'), cliente: malha.cliente),
    );

    await monitor.consultar();

    expect(monitor.emEleicao, isTrue);
  });
}
