/// Dinheiro em CENTAVOS inteiros.
///
/// O backend usa BigDecimal; em Dart, somar saldo + pendentes em double daria
/// 0.1 + 0.2 = 0.30000000000000004 no saldo provisorio. Centavos inteiros somam
/// exato, e a conversao acontece so na borda (JSON e tela).
int centavosDe(Object? valor) => ((valor as num) * 100).round();

double reaisDe(int centavos) => centavos / 100;

String formatarReais(int centavos) {
  final negativo = centavos < 0;
  final abs = centavos.abs();
  final inteiro = (abs ~/ 100).toString();
  final comPontos = inteiro.replaceAllMapped(
    RegExp(r'\B(?=(\d{3})+(?!\d))'),
    (_) => '.',
  );
  final centavosTexto = (abs % 100).toString().padLeft(2, '0');
  return '${negativo ? '-' : ''}R\$ $comPontos,$centavosTexto';
}

/// Le "10", "10,5" ou "10.50" digitado pela pessoa. null se nao for valor positivo.
int? centavosDigitados(String texto) {
  final normalizado = texto.trim().replaceAll('.', '').replaceAll(',', '.');
  final valor = double.tryParse(
    texto.contains(',') ? normalizado : texto.trim(),
  );
  if (valor == null || valor <= 0) return null;
  return (valor * 100).round();
}
