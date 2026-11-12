class Malha {
  static const int portaBase = 4016;

  final String host;
  final int total;

  const Malha({required this.host, this.total = 3});

  Uri url(int idAgencia, String caminho) =>
      Uri.parse('http://$host:${portaBase + idAgencia}$caminho');

  int agenciaDaConta(int idConta) => idConta % total;

  Iterable<int> get agencias => Iterable<int>.generate(total);
}
