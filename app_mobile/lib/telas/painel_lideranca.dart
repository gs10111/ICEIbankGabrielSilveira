import 'package:material_ui/material_ui.dart';

import '../estado/monitor_lideranca.dart';

class PainelLideranca extends StatelessWidget {
  final MonitorLideranca monitor;

  const PainelLideranca({super.key, required this.monitor});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: monitor,
      builder: (context, _) {
        final cores = Theme.of(context).colorScheme;
        final texto = Theme.of(context).textTheme;
        final (titulo, cor, icone) = switch (monitor) {
          MonitorLideranca(nenhumaRespondeu: true) => (
            'Nenhuma agência respondeu',
            cores.error,
            Icons.cloud_off,
          ),
          MonitorLideranca(atualizadoEm: null) => (
            'Consultando…',
            cores.outline,
            Icons.hourglass_empty,
          ),
          MonitorLideranca(emEleicao: true) => (
            'Eleição em andamento',
            cores.tertiary,
            Icons.how_to_vote,
          ),
          _ => (
            'Agência ${monitor.lider}',
            cores.primary,
            Icons.workspace_premium,
          ),
        };
        return Card(
          key: const Key('painel-lideranca'),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(icone, color: cor, size: 40),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Líder atual (Bully)', style: texto.labelLarge),
                          Text(
                            titulo,
                            style: texto.headlineSmall?.copyWith(color: cor),
                          ),
                          if (monitor.atualizadoEm != null)
                            Text(
                              '${monitor.nenhumaRespondeu ? 'tentou as 3 agências · ' : 'consultada na agência ${monitor.consultadaEm} · '}'
                              'às ${_hora(monitor.atualizadoEm!)}',
                              style: texto.bodySmall,
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
                if (monitor.ativas.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  _AgenciasAtivas(monitor: monitor),
                ],
                if (monitor.divergencia)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      'As agências ainda não concordam: troca de liderança em andamento.',
                      key: const Key('divergencia'),
                      style: texto.bodySmall?.copyWith(color: cores.tertiary),
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  static String _hora(DateTime t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:${t.second.toString().padLeft(2, '0')}';
}

class _AgenciasAtivas extends StatelessWidget {
  final MonitorLideranca monitor;

  const _AgenciasAtivas({required this.monitor});

  @override
  Widget build(BuildContext context) {
    final cores = Theme.of(context).colorScheme;
    return Wrap(
      spacing: 8,
      children: [
        for (final MapEntry(key: id, value: viva) in monitor.ativas.entries)
          Chip(
            key: Key('agencia-$id'),
            avatar: Icon(
              id == monitor.lider && viva
                  ? Icons.workspace_premium
                  : (viva ? Icons.circle : Icons.cancel),
              size: 16,
              color: viva ? Colors.green.shade700 : cores.error,
            ),
            label: Text('AG $id · ${viva ? 'no ar' : 'fora'}'),
          ),
      ],
    );
  }
}
