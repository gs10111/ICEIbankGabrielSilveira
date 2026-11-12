import 'package:material_ui/material_ui.dart';

import '../estado/banco.dart';
import '../modelo/dinheiro.dart';
import '../modelo/operacao_pendente.dart';
import 'formularios.dart';
import 'login_tela.dart';
import 'tema.dart';

class ContaTela extends StatelessWidget {
  final Banco banco;
  final List<Widget> topo;

  const ContaTela({super.key, required this.banco, this.topo = const []});

  Future<void> _executar(
    BuildContext context,
    Future<String> Function() operacao,
  ) async {
    try {
      final mensagem = await operacao();
      if (context.mounted) avisar(context, mensagem);
    } catch (e) {
      if (context.mounted) mostrarErro(context, e);
    }
  }

  Future<void> _depositar(BuildContext context) async {
    final pedido = await pedirValor(context, 'Depósito');
    if (pedido != null && context.mounted) {
      await _executar(context, () => banco.depositar(pedido.centavos));
    }
  }

  Future<void> _sacar(BuildContext context) async {
    final pedido = await pedirValor(context, 'Saque');
    if (pedido != null && context.mounted) {
      await _executar(context, () => banco.sacar(pedido.centavos));
    }
  }

  Future<void> _transferir(BuildContext context) async {
    final pedido = await pedirValor(context, 'Transferência', comDestino: true);
    if (pedido != null && context.mounted) {
      await _executar(
        context,
        () => banco.transferir(pedido.destino!, pedido.centavos),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final sessao = banco.sessao!;
    return Scaffold(
      appBar: AppBar(
        title: Text('Olá, ${sessao.nome.split(' ').first}'),
        actions: [
          IconButton(
            tooltip: 'Sair',
            icon: const Icon(Icons.logout),
            onPressed: banco.sair,
          ),
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
            if (banco.offline) _AvisoOffline(banco: banco),
            for (final op in banco.pendentes.where(
              (p) => p.estado != EstadoPendente.pendente,
            ))
              _CartaoRecusa(banco: banco, op: op),
            _SaldoCartao(banco: banco),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: FilledButton.tonalIcon(
                    onPressed: () => _depositar(context),
                    icon: const Icon(Icons.add),
                    label: const Text('Depositar'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: FilledButton.tonalIcon(
                    onPressed: () => _sacar(context),
                    icon: const Icon(Icons.remove),
                    label: const Text('Sacar'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: () => _transferir(context),
              icon: const Icon(Icons.swap_horiz),
              label: const Text('Transferir'),
            ),
            _ListaPendentes(banco: banco),
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
    final provisorio = banco.saldoProvisorioCentavos;
    final emEspera = banco.pendentes
        .where((p) => p.estado == EstadoPendente.pendente)
        .length;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Conta ${banco.sessao!.idConta} · Agência ${banco.sessao!.agencia}',
              style: texto.labelLarge,
            ),
            const SizedBox(height: 8),
            Text(
              provisorio == null ? '—' : formatarReais(provisorio),
              style: texto.displaySmall,
            ),
            if (emEspera > 0) ...[
              const SizedBox(height: 4),
              Chip(
                key: const Key('saldo-provisorio'),
                avatar: const Icon(Icons.schedule, size: 18),
                label: Text(
                  'Provisório · $emEspera pendente${emEspera > 1 ? 's' : ''}',
                ),
                backgroundColor: Theme.of(context)
                    .colorScheme
                    .tertiaryContainer,
              ),
              Text(
                'Confirmado pela agência: ${formatarReais(conta!.saldoCentavos)}',
                style: texto.bodyMedium,
              ),
            ],
            if (conta != null)
              Text(
                'vetor da agência na leitura: ${conta.vetor}',
                style: texto.bodySmall,
              ),
          ],
        ),
      ),
    );
  }
}

class _AvisoOffline extends StatelessWidget {
  final Banco banco;

  const _AvisoOffline({required this.banco});

  @override
  Widget build(BuildContext context) {
    final lida = banco.conta?.lidaEm;
    return Card(
      key: const Key('aviso-offline'),
      color: Theme.of(context).colorScheme.secondaryContainer,
      child: ListTile(
        leading: const Icon(Icons.cloud_off),
        title: const Text('Sem conexão com a agência'),
        subtitle: Text(
          'Mostrando o último saldo guardado'
          '${lida == null ? '' : ' (lido às ${_hora(lida)})'}. '
          'Depósitos e saques ficam pendentes e sincronizam sozinhos quando a conexão voltar.',
        ),
      ),
    );
  }
}

class _CartaoRecusa extends StatelessWidget {
  final Banco banco;
  final OperacaoPendente op;

  const _CartaoRecusa({required this.banco, required this.op});

  @override
  Widget build(BuildContext context) {
    final cores = Theme.of(context).colorScheme;
    final conflito = op.estado == EstadoPendente.conflito;
    return Card(
      key: Key('recusa-${op.chave}'),
      color: cores.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  conflito ? Icons.sync_problem : Icons.block,
                  color: cores.onErrorContainer,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Não aplicada: ${op.descricao}',
                    style: Theme.of(context).textTheme.titleMedium
                        ?.copyWith(color: cores.onErrorContainer),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              conflito
                  ? 'A conta mudou na agência enquanto o app estava offline, então a operação '
                        'NÃO foi aplicada automaticamente. Saldo real agora: '
                        '${formatarReais(op.saldoRealCentavos ?? banco.conta!.saldoCentavos)}.'
                  : 'A agência recusou: ${op.mensagem}',
              style: TextStyle(color: cores.onErrorContainer),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                if (conflito)
                  FilledButton(
                    onPressed: () => banco.reenviar(op),
                    child: const Text('Aplicar mesmo assim'),
                  ),
                OutlinedButton(
                  onPressed: () => banco.descartar(op),
                  child: const Text('Descartar'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ListaPendentes extends StatelessWidget {
  final Banco banco;

  const _ListaPendentes({required this.banco});

  @override
  Widget build(BuildContext context) {
    final fila = banco.pendentes
        .where((p) => p.estado == EstadoPendente.pendente)
        .toList();
    if (fila.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 16),
      child: Card(
        key: const Key('lista-pendentes'),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: Text(
                'Pendentes de sincronização',
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ),
            for (final op in fila)
              ListTile(
                leading: Icon(
                  op.ehDeposito
                      ? Icons.add_circle_outline
                      : Icons.remove_circle_outline,
                ),
                title: Text(op.descricao),
                subtitle: Text(
                  'feita offline às ${_hora(op.criadaEm)} · sobre o vetor ${op.vetorConhecido}',
                ),
                trailing: const Chip(label: Text('pendente')),
              ),
          ],
        ),
      ),
    );
  }
}

String _hora(DateTime t) =>
    '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:${t.second.toString().padLeft(2, '0')}';
