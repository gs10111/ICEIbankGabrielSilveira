import 'package:material_ui/material_ui.dart';

ThemeData temaIceiBank() => ThemeData(
  colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF0B5D63)),
  useMaterial3: true,
  inputDecorationTheme: const InputDecorationTheme(
    border: OutlineInputBorder(),
  ),
);

/// Mensagem de erro/sucesso no rodape. Erro fica vermelho e mais tempo na tela:
/// o roteiro pede tratamento VISIVEL de erro, nao um print no console.
void avisar(BuildContext context, String mensagem, {bool erro = false}) {
  final cores = Theme.of(context).colorScheme;
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        content: Text(mensagem),
        backgroundColor: erro ? cores.error : null,
        duration: Duration(seconds: erro ? 6 : 3),
      ),
    );
}
