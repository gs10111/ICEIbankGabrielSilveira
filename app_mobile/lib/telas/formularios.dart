import 'package:material_ui/material_ui.dart';

import '../modelo/dinheiro.dart';

/// Resultado de um formulario: valor em centavos e, na transferencia, o destino.
class Pedido {
  final int centavos;
  final int? destino;

  const Pedido(this.centavos, [this.destino]);
}

Future<Pedido?> pedirValor(
  BuildContext context,
  String titulo, {
  bool comDestino = false,
}) {
  return showModalBottomSheet<Pedido>(
    context: context,
    isScrollControlled: true,
    builder: (_) => _Formulario(titulo: titulo, comDestino: comDestino),
  );
}

class _Formulario extends StatefulWidget {
  final String titulo;
  final bool comDestino;

  const _Formulario({required this.titulo, required this.comDestino});

  @override
  State<_Formulario> createState() => _FormularioState();
}

class _FormularioState extends State<_Formulario> {
  final _valor = TextEditingController();
  final _destino = TextEditingController();
  String? _erro;

  void _confirmar() {
    final centavos = centavosDigitados(_valor.text);
    final destino = int.tryParse(_destino.text.trim());
    if (centavos == null) {
      setState(() => _erro = 'Informe um valor positivo.');
    } else if (widget.comDestino && (destino == null || destino < 0)) {
      setState(() => _erro = 'Informe a conta de destino.');
    } else {
      Navigator.pop(context, Pedido(centavos, destino));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(
        24,
        24,
        24,
        24 + MediaQuery.of(context).viewInsets.bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(widget.titulo, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          if (widget.comDestino) ...[
            TextField(
              controller: _destino,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Conta de destino',
                helperText: 'Pode ser de outra agência (conta % 3)',
              ),
            ),
            const SizedBox(height: 12),
          ],
          TextField(
            controller: _valor,
            autofocus: !widget.comDestino,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(
              labelText: 'Valor (R\$)',
              errorText: _erro,
            ),
            onSubmitted: (_) => _confirmar(),
          ),
          const SizedBox(height: 16),
          FilledButton(onPressed: _confirmar, child: const Text('Confirmar')),
        ],
      ),
    );
  }
}
