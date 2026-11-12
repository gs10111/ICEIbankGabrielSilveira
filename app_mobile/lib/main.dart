import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:hive_ce_flutter/hive_ce_flutter.dart';
import 'package:material_ui/material_ui.dart';

import 'api/api_cliente.dart';
import 'dados/armazenamento.dart';
import 'estado/banco.dart';
import 'estado/gatilho_sincronizacao.dart';
import 'estado/monitor_lideranca.dart';
import 'modelo/malha.dart';
import 'telas/conta_tela.dart';
import 'telas/login_tela.dart';
import 'telas/painel_lideranca.dart';
import 'telas/tema.dart';

String hostPadrao() => kIsWeb
    ? Uri.base.host
    : const String.fromEnvironment('ICEIBANK_HOST', defaultValue: '10.0.2.2');

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Hive.initFlutter();
  final armazenamento = await Armazenamento.abrir();

  final api = ApiCliente(Malha(host: armazenamento.host ?? hostPadrao()));
  final banco = Banco(api, armazenamento)..restaurar();
  GatilhoDeSincronizacao(
    banco,
    conectividade: Connectivity().onConnectivityChanged,
  ).iniciar();
  if (banco.logado) banco.atualizar();

  runApp(IceiBankApp(banco: banco, monitor: MonitorLideranca(api)..iniciar()));
}

class IceiBankApp extends StatelessWidget {
  final Banco banco;
  final MonitorLideranca monitor;

  const IceiBankApp({super.key, required this.banco, required this.monitor});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ICEIBank',
      theme: temaIceiBank(),
      home: ListenableBuilder(
        listenable: banco,
        builder: (context, _) => banco.logado
            ? ContaTela(
                banco: banco,
                topo: [
                  PainelLideranca(monitor: monitor),
                  const SizedBox(height: 8),
                ],
              )
            : LoginTela(
                banco: banco,
                rodape: [PainelLideranca(monitor: monitor)],
              ),
      ),
    );
  }
}
