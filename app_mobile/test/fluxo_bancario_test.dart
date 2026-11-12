import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:iceibank_mobile/api/api_cliente.dart';
import 'package:iceibank_mobile/estado/banco.dart';
import 'package:iceibank_mobile/estado/monitor_lideranca.dart';
import 'package:iceibank_mobile/main.dart';
import 'package:iceibank_mobile/modelo/malha.dart';
import 'package:material_ui/material_ui.dart';

/// Agencia falsa: uma conta, saldo em memoria, as mesmas respostas do backend.
MockClient agenciaFalsa() {
  var saldo = 100.0;
  http.Response conta() => http.Response(
      jsonEncode({'id': 0, 'nomeAluno': 'Ana Souza', 'saldo': saldo, 'agencia': 0, 'vetorAtual': [1, 0, 0]}), 200);
  return MockClient((r) async {
    final corpo = r.body.isEmpty ? {} : jsonDecode(r.body) as Map;
    switch (r.url.path) {
      case '/auth/login':
        if (corpo['senha'] != 'ana123') return http.Response(jsonEncode({'erro': 'credenciais invalidas'}), 401);
        return http.Response(jsonEncode({'token': 't', 'idConta': 0, 'nomeAluno': 'Ana Souza', 'agencia': 0}), 200);
      case '/contas/0/depositar':
        saldo += corpo['valor'] as num;
        return conta();
      case '/contas/0/sacar':
        if ((corpo['valor'] as num) > saldo) {
          return http.Response(jsonEncode({'erro': 'saldo insuficiente: saldo=$saldo'}), 400);
        }
        saldo -= corpo['valor'] as num;
        return conta();
      default:
        return conta();
    }
  });
}

Future<void> entrar(WidgetTester tester, String senha) async {
  await tester.enterText(find.widgetWithText(TextField, 'Senha'), senha);
  await tester.tap(find.text('Entrar'));
  await tester.pumpAndSettle();
}

Future<void> operar(WidgetTester tester, String botao, String valor) async {
  await tester.tap(find.text(botao));
  await tester.pumpAndSettle();
  await tester.enterText(find.widgetWithText(TextField, r'Valor (R$)'), valor);
  await tester.tap(find.text('Confirmar'));
  await tester.pumpAndSettle();
}

IceiBankApp app() {
  final api = ApiCliente(const Malha(host: 'h'), cliente: agenciaFalsa());
  return IceiBankApp(banco: Banco(api), monitor: MonitorLideranca(api));
}

void main() {
  testWidgets('login, saldo, deposito e saque pela interface', (tester) async {
    await tester.pumpWidget(app());

    await entrar(tester, 'ana123');
    expect(find.text('Olá, Ana'), findsOneWidget);
    expect(find.text(r'R$ 100,00'), findsOneWidget);

    await operar(tester, 'Depositar', '50');
    expect(find.text(r'R$ 150,00'), findsOneWidget);

    await operar(tester, 'Sacar', '20,50');
    expect(find.text(r'R$ 129,50'), findsOneWidget);
  });

  testWidgets('erro da API aparece na tela: senha errada e saldo insuficiente', (tester) async {
    await tester.pumpWidget(app());

    await entrar(tester, 'errada');
    expect(find.text('credenciais invalidas'), findsOneWidget);

    await entrar(tester, 'ana123');
    await operar(tester, 'Sacar', '999');
    expect(find.textContaining('saldo insuficiente'), findsOneWidget);
  });
}
