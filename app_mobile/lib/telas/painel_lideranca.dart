import 'package:material_ui/material_ui.dart';

import '../estado/monitor_lideranca.dart';

/// O consenso na tela: qual agencia e a lider agora. Atualiza sozinho a cada
/// consulta do MonitorLideranca — ninguem precisa puxar para atualizar.
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
          MonitorLideranca(nenhumaRespondeu: true) => ('Nenhuma agência respondeu', cores.error, Icons.cloud_off),
          MonitorLideranca(atualizadoEm: null) => ('Consultando…', cores.outline, Icons.hourglass_empty),
          MonitorLideranca(emEleicao: true) => ('Eleição em andamento', cores.tertiary, Icons.how_to_vote),
          _ => ('Agência ${monitor.lider}', cores.primary, Icons.workspace_premium),
        };
        return Card(
          key: const Key('painel-lideranca'),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(children: [
              Icon(icone, color: cor, size: 40),
              const SizedBox(width: 16),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('Líder atual (Bully)', style: texto.labelLarge),
                  Text(titulo, style: texto.headlineSmall?.copyWith(color: cor)),
                  if (monitor.atualizadoEm != null)
                    Text(
                      '${monitor.consultadaEm == null ? '' : 'consultada na agência ${monitor.consultadaEm} · '}'
                      'às ${_hora(monitor.atualizadoEm!)}',
                      style: texto.bodySmall,
                    ),
                ]),
              ),
            ]),
          ),
        );
      },
    );
  }

  static String _hora(DateTime t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:${t.second.toString().padLeft(2, '0')}';
}
