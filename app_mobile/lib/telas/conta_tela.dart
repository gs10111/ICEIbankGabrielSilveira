import 'package:material_ui/material_ui.dart';

import '../estado/banco.dart';
import '../modelo/dinheiro.dart';
import 'formularios.dart';
import 'login_tela.dart';
import 'tema.dart';

class ContaTela extends StatelessWidget {
  final Banco banco;
  final List<Widget> topo;

  const ContaTela({super.key, required this.banco, this.topo = const []});

  Future<void> _executar(BuildContext context, Future<String> Function() operacao) async {
    try {
      final mensagem = await operacao();
      if (context.mounted) avisar(context, mensagem);
    } catch (e) {
      if (context.mounted) mostrarErro(context, e);
    }
  }

  Future<void> _depositar(BuildContext context) async {
    final pedido = await pedirValor(context, 'Depósito');
    if (pedido != null && context.mounted) await _executar(context, () => banco.depositar(pedido.centavos));
  }

  Future<void> _sacar(BuildContext context) async {
    final pedido = await pedirValor(context, 'Saque');
    if (pedido != null && context.mounted) await _executar(context, () => banco.sacar(pedido.centavos));
  }

  Future<void> _transferir(BuildContext context) async {
    final pedido = await pedirValor(context, 'Transferência', comDestino: true);
    if (pedido != null && context.mounted) {
      await _executar(context, () => banco.transferir(pedido.destino!, pedido.centavos));
    }
  }

  @override
  Widget build(BuildContext context) {
    final sessao = banco.sessao!;
    return Scaffold(
      appBar: AppBar(
        title: Text('Olá, ${sessao.nome.split(' ').first}'),
        actions: [
          IconButton(tooltip: 'Sair', icon: const Icon(Icons.logout), onPressed: banco.sair),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => _executar(context, () async {
          await banco.atualizar();
          return 'Saldo atualizado.';
        }),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            ...topo,
            _SaldoCartao(banco: banco),
            const SizedBox(height: 16),
            Row(children: [
              Expanded(
                  child: FilledButton.tonalIcon(
                      onPressed: () => _depositar(context), icon: const Icon(Icons.add), label: const Text('Depositar'))),
              const SizedBox(width: 8),
              Expanded(
                  child: FilledButton.tonalIcon(
                      onPressed: () => _sacar(context), icon: const Icon(Icons.remove), label: const Text('Sacar'))),
            ]),
            const SizedBox(height: 8),
            OutlinedButton.icon(
                onPressed: () => _transferir(context),
                icon: const Icon(Icons.swap_horiz),
                label: const Text('Transferir')),
          ],
        ),
      ),
    );
  }
}

class _SaldoCartao extends StatelessWidget {
  final Banco banco;

  const _SaldoCartao({required this.banco});

  @override
  Widget build(BuildContext context) {
    final conta = banco.conta;
    final texto = Theme.of(context).textTheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Conta ${banco.sessao!.idConta} · Agência ${banco.sessao!.agencia}', style: texto.labelLarge),
          const SizedBox(height: 8),
          Text(conta == null ? '—' : formatarReais(conta.saldoCentavos), style: texto.displaySmall),
          if (conta != null) Text('vetor da agência na leitura: ${conta.vetor}', style: texto.bodySmall),
        ]),
      ),
    );
  }
}
