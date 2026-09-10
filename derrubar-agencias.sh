#!/bin/bash
# Encerra as 3 agencias. Para derrubar SO UMA (falha conhecida da Parte D):
#     fuser -k 4017/tcp        # agencia 1
#
# O colchete em [i]ceibank e proposital: sem ele o padrao casa com a propria
# linha de comando do pkill, que se mata antes de matar as agencias.
pkill -f "[i]ceibank-agencia-1.0.0.jar" && echo "agencias encerradas" || echo "nenhuma agencia rodando"
exit 0
