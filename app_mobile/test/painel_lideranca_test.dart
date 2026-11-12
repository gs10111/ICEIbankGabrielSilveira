import 'package:flutter_test/flutter_test.dart';
import 'package:iceibank_mobile/api/api_cliente.dart';
import 'package:iceibank_mobile/estado/monitor_lideranca.dart';
import 'package:iceibank_mobile/modelo/malha.dart';
import 'package:iceibank_mobile/telas/painel_lideranca.dart';
import 'package:material_ui/material_ui.dart';

import 'monitor_lideranca_test.dart' show Malha3;

void main() {
  testWidgets(
    'o painel troca sozinho para a nova lider depois da queda (roteiro 8.2, passo 4)',
    (tester) async {
      final malha = Malha3();
      final monitor = MonitorLideranca(
        ApiCliente(const Malha(host: 'h'), cliente: malha.cliente),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: PainelLideranca(monitor: monitor)),
        ),
      );

      monitor.iniciar();
      await tester.pump();
      await tester.pump();
      expect(find.text('Agência 2'), findsOneWidget);

      malha.fora.add(2);
      malha.lider = 1;
      await tester.pump(
        const Duration(seconds: 3),
      ); // um ciclo do polling, nenhum toque na tela
      await tester.pump();

      expect(find.text('Agência 1'), findsOneWidget);
      monitor.parar();
    },
  );

  testWidgets(
    'funcionalidade adicional: mostra as 3 agencias e quem esta fora',
    (tester) async {
      final malha = Malha3()
        ..fora.add(2)
        ..lider = 1;
      final monitor = MonitorLideranca(
        ApiCliente(const Malha(host: 'h'), cliente: malha.cliente),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: PainelLideranca(monitor: monitor)),
        ),
      );

      await monitor.consultar();
      await tester.pump();

      expect(find.text('AG 0 · no ar'), findsOneWidget);
      expect(find.text('AG 1 · no ar'), findsOneWidget);
      expect(find.text('AG 2 · fora'), findsOneWidget);
    },
  );
}
