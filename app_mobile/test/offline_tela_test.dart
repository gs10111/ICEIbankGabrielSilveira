import 'package:flutter_test/flutter_test.dart';
import 'package:hive_ce/hive_ce.dart';
import 'package:iceibank_mobile/api/api_cliente.dart';
import 'package:iceibank_mobile/dados/armazenamento.dart';
import 'package:iceibank_mobile/estado/banco.dart';
import 'package:iceibank_mobile/estado/monitor_lideranca.dart';
import 'package:iceibank_mobile/main.dart';
import 'package:iceibank_mobile/modelo/malha.dart';
import 'package:material_ui/material_ui.dart';

import 'agencia_falsa.dart';

void main() {
  late AgenciaFalsa agencia;
  late Banco banco;

  setUp(() async {
    agencia = AgenciaFalsa();
    final api = ApiCliente(const Malha(host: 'h'), cliente: agencia.cliente);
    banco = Banco(api, await Armazenamento.emMemoria());
    await banco.entrar(0, 'ana123');
  });

  tearDown(Hive.close);

  Future<void> abrir(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1080, 3200);   // tela alta: a lista de pendentes cabe sem rolar
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(IceiBankApp(banco: banco, monitor: MonitorLideranca(banco.api)));
    await tester.pumpAndSettle();
  }

  Future<void> depositar(WidgetTester tester, String valor) async {
    await tester.tap(find.text('Depositar'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, r'Valor (R$)'), valor);
    await tester.tap(find.text('Confirmar'));
    await tester.pumpAndSettle();
  }

  testWidgets('modo aviao: deposito aparece como pendente, saldo provisorio ja atualizado', (tester) async {
    await abrir(tester);
    agencia.online = false;

    await depositar(tester, '50');

    expect(find.byKey(const Key('aviso-offline')), findsOneWidget);
    expect(find.text(r'R$ 150,00'), findsOneWidget);
    expect(find.byKey(const Key('saldo-provisorio')), findsOneWidget);
    expect(find.text(r'Confirmado pela agência: R$ 100,00'), findsOneWidget);
    expect(find.byKey(const Key('lista-pendentes')), findsOneWidget);
    expect(find.text('pendente'), findsOneWidget);
  });

  testWidgets('reconectou: a pendente some e o saldo vira confirmado', (tester) async {
    await abrir(tester);
    agencia.online = false;
    await depositar(tester, '50');

    agencia.online = true;
    await banco.sincronizarPendentes();
    await banco.atualizar();
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('lista-pendentes')), findsNothing);
    expect(find.byKey(const Key('aviso-offline')), findsNothing);
    expect(find.text(r'R$ 150,00'), findsOneWidget);
  });

  testWidgets('409: a tela diz que NAO foi aplicada, mostra o saldo real e pede uma decisao', (tester) async {
    await abrir(tester);
    agencia.online = false;
    await depositar(tester, '50');
    agencia.mudarPorOutroCaminho(30);
    agencia.online = true;

    await banco.sincronizarPendentes();
    await tester.pumpAndSettle();

    expect(find.textContaining(r'Não aplicada: Depósito de R$ 50,00'), findsOneWidget);
    expect(find.textContaining(r'Saldo real agora: R$ 130,00'), findsOneWidget);
    expect(find.text('Aplicar mesmo assim'), findsOneWidget);
    expect(find.text('Descartar'), findsOneWidget);

    await tester.tap(find.text('Aplicar mesmo assim'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Não aplicada'), findsNothing);
    expect(find.text(r'R$ 180,00'), findsOneWidget);
  });
}
