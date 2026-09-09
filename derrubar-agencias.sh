#!/bin/bash
# Encerra as 3 agencias. Para derrubar SO UMA (falha conhecida da Parte D):
#     fuser -k 4017/tcp        # agencia 1
pkill -f "iceibank-agencia-1.0.0.jar" && echo "agencias encerradas" || echo "nenhuma agencia rodando"
