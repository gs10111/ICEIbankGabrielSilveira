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

int? centavosDigitados(String texto) {
  final normalizado = texto.trim().replaceAll('.', '').replaceAll(',', '.');
  final valor = double.tryParse(
    texto.contains(',') ? normalizado : texto.trim(),
  );
  if (valor == null || valor <= 0) return null;
  return (valor * 100).round();
}
