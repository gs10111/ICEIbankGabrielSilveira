#!/bin/bash
pkill -f "[i]ceibank-agencia-1.0.0.jar" && echo "agencias encerradas" || echo "nenhuma agencia rodando"
exit 0
