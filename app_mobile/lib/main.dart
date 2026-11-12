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

/// No emulador Android o PC e 10.0.2.2; na web, o mesmo host de onde a pagina veio.
/// `flutter run --dart-define=ICEIBANK_HOST=192.168.0.10` para um celular de verdade.
String hostPadrao() =>
    kIsWeb ? Uri.base.host : const String.fromEnvironment('ICEIBANK_HOST', defaultValue: '10.0.2.2');

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Hive.initFlutter();
  final armazenamento = await Armazenamento.abrir();

  final api = ApiCliente(Malha(host: armazenamento.host ?? hostPadrao()));
  // Reabre logado com o ultimo saldo guardado, mesmo em modo aviao (Parte F).
  final banco = Banco(api, armazenamento)..restaurar();
  GatilhoDeSincronizacao(banco, conectividade: Connectivity().onConnectivityChanged).iniciar();
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
        // O painel aparece nas duas telas: GET /bully/lider e publico, entao da para
        // acompanhar o consenso antes mesmo do login.
        builder: (context, _) => banco.logado
            ? ContaTela(banco: banco, topo: [PainelLideranca(monitor: monitor), const SizedBox(height: 8)])
            : LoginTela(banco: banco, rodape: [PainelLideranca(monitor: monitor)]),
      ),
    );
  }
}
