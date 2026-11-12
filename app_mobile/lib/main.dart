import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';

import 'api/api_cliente.dart';
import 'estado/banco.dart';
import 'modelo/malha.dart';
import 'telas/conta_tela.dart';
import 'telas/login_tela.dart';
import 'telas/tema.dart';

/// No emulador Android o PC e 10.0.2.2; na web, o mesmo host de onde a pagina veio.
/// `flutter run --dart-define=ICEIBANK_HOST=192.168.0.10` para um celular de verdade.
String hostPadrao() =>
    kIsWeb ? Uri.base.host : const String.fromEnvironment('ICEIBANK_HOST', defaultValue: '10.0.2.2');

void main() {
  runApp(IceiBankApp(banco: Banco(ApiCliente(Malha(host: hostPadrao())))));
}

class IceiBankApp extends StatelessWidget {
  final Banco banco;

  const IceiBankApp({super.key, required this.banco});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ICEIBank',
      theme: temaIceiBank(),
      home: ListenableBuilder(
        listenable: banco,
        builder: (context, _) => banco.logado ? ContaTela(banco: banco) : LoginTela(banco: banco),
      ),
    );
  }
}
