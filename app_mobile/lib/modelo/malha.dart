/// A malha de agencias do ICEIBank vista pelo app.
///
/// Mesma regra do frontend web (modelo/agencias.js): a conta N pertence a agencia
/// N % total, e a agencia i responde na porta 4016 + i (OFFSET pessoal 16).
///
/// O host NAO e fixo: no emulador Android o computador e 10.0.2.2; num celular de
/// verdade e o IP da maquina na rede do laboratorio. Por isso ele e editavel na
/// tela de login e guardado junto com a sessao.
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
