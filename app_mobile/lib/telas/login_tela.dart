import 'package:material_ui/material_ui.dart';

import '../estado/banco.dart';
import '../modelo/malha.dart';
import 'tema.dart';

class LoginTela extends StatefulWidget {
  final Banco banco;
  final List<Widget> rodape;

  const LoginTela({super.key, required this.banco, this.rodape = const []});

  @override
  State<LoginTela> createState() => _LoginTelaState();
}

class _LoginTelaState extends State<LoginTela> {
  late final _host = TextEditingController(text: widget.banco.api.malha.host);
  final _conta = TextEditingController(text: '0');
  final _senha = TextEditingController();
  bool _entrando = false;
  String? _erro;

  Future<void> _entrar() async {
    final idConta = int.tryParse(_conta.text.trim());
    if (idConta == null || idConta < 0) {
      setState(() => _erro = 'Número de conta inválido.');
      return;
    }
    setState(() {
      _entrando = true;
      _erro = null;
    });
    widget.banco.api.malha = Malha(host: _host.text.trim());
    try {
      await widget.banco.entrar(idConta, _senha.text);
    } catch (e) {
      if (mounted) setState(() => _erro = e.toString());
    } finally {
      if (mounted) setState(() => _entrando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final aviso = widget.banco.avisoDeSessao;
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            const SizedBox(height: 24),
            Text('ICEIBank', style: Theme.of(context).textTheme.displaySmall),
            const Text('Login com JWT — a mesma API do frontend web.'),
            const SizedBox(height: 24),
            if (aviso != null) ...[
              Card(
                color: Theme.of(context).colorScheme.errorContainer,
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text(aviso),
                ),
              ),
              const SizedBox(height: 12),
            ],
            TextField(
              controller: _host,
              decoration: const InputDecoration(
                labelText: 'Host das agências',
                helperText: 'Emulador Android: 10.0.2.2 · celular: IP do PC',
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _conta,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Número da conta'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _senha,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'Senha'),
              onSubmitted: (_) => _entrar(),
            ),
            if (_erro != null) ...[
              const SizedBox(height: 12),
              Text(
                _erro!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _entrando ? null : _entrar,
              child: Text(_entrando ? 'Entrando…' : 'Entrar'),
            ),
            const SizedBox(height: 24),
            ...widget.rodape,
          ],
        ),
      ),
    );
  }
}

/// Usado pela tela de conta tambem.
void mostrarErro(BuildContext context, Object erro) =>
    avisar(context, erro.toString(), erro: true);
