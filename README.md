# Finanças — controle financeiro para Android

App nativo em **Kotlin + Jetpack Compose (Material 3)**, com armazenamento local em **Room (SQLite)** e sem login.

## Como abrir e rodar

1. Abra a pasta `financas` no **Android Studio** (Narwhal 2025.1 ou mais recente).
2. Aguarde o *Gradle Sync* (ele baixa o Gradle 8.14.3 e as dependências).
3. Conecte um celular com Android 8.0+ (ou use um emulador) e clique em **Run ▶**.

Para gerar um APK instalável: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
(assinado com a chave fixa `app/financas.keystore`, então cada APK novo atualiza o anterior sem perder dados).

Testes da lógica de negócio: `./gradlew testDebugUnitTest`

### Gerar o APK sem Android Studio (GitHub Actions)

O projeto já traz `.github/workflows/build-apk.yml`. Para usar:

1. Envie o projeto para um repositório no GitHub (branch `main`).
2. O GitHub compila sozinho a cada envio.
3. O APK aparece em **Releases › latest › financas.apk**, com link direto para baixar no celular.

O log da compilação fica no branch `build-output`.

## O que tem no app

| Tela | O que faz |
|---|---|
| **Início** | Saldo das contas, entradas/saídas do mês, faturas abertas, próximas recorrentes e últimos lançamentos |
| **Lançamentos** | Lista por dia com filtros de mês, conta/cartão, categoria, pessoa e busca |
| **Novo lançamento** | Conta (saída/entrada) ou cartão (compra/estorno, parcelas e quem paga, com divisão igual ou personalizada) |
| **Relatórios** | Visão anual ou mensal agrupada por mês, categoria, conta, cartão ou pessoa; “marcar como pago” por pessoa |
| **Importar OFX** | Arquivo → destino e opções → revisão → importação |
| **Cadastros** | Contas, cartões, recorrentes, categorias, pessoas, backup e restauração |

## Regras importantes

- **Valores** são guardados em centavos (`Long`), sem erros de arredondamento.
- **Fatura do cartão** é identificada pelo mês em que fecha. Compras antes do dia de fechamento entram na fatura do mês. Compras no dia do fechamento ou depois entram na do mês seguinte. O vencimento cai no mesmo mês se o dia de vencimento for maior que o de fechamento; caso contrário, no mês seguinte.
- **Parcelas** são geradas ao salvar, uma por fatura. Os centavos que sobram vão para as primeiras parcelas.
- **Compras no cartão** contam no mês da fatura, tanto nos relatórios quanto na lista de lançamentos.
- **Pessoas**: cada compra pode ser dividida entre várias pessoas (inclusive “Eu”). O relatório por pessoa mostra quanto cada uma deve em cada cartão.
- **Categorias** marcadas como “ignorar nos relatórios” ficam fora dos totais. Já vêm marcadas: *Pagamento de fatura* e *Entre minhas contas*.
- **Recorrentes** têm uma data-âncora e uma frequência (semanal, mensal, anual). As próximas aparecem no Início e podem ser lançadas com um toque.

## Importador de OFX e CSV

- Lê OFX 1.x (SGML) e 2.x (XML), e CSV com cabeçalho (ex.: `Data;Estabelecimento;Portador;Valor;Parcela`), separado por `;`, `,` ou tab.
- No CSV de fatura, as compras vêm positivas e o pagamento negativo. O app inverte os sinais e usa a coluna *Parcela* (“2 de 3”) para reconhecer parcelas.
- Como o CSV não tem FITID, cada linha recebe um identificador estável. Assim, reimportar o mesmo arquivo não duplica nada.
- Lê OFX Detecta o encoding (UTF-8 ou Windows-1252, padrão dos bancos brasileiros).
- Identifica se o arquivo é de **conta** ou de **cartão** e sugere o destino. Na próxima importação, lembra qual conta/cartão corresponde a cada arquivo.
- Para cartão, infere a **fatura** pela compra mais recente. Você pode ajustar antes de revisar.
- **Duplicadas**:
  - Mesmo FITID (o identificador da transação no OFX).
  - Lançamento manual com mesma data, valor e direção.
  - Parcela já projetada por uma importação anterior.
- **Sugestão de descrição e categoria** com base no histórico, incluindo o texto original de importações anteriores. Também reconhece PIX/TED/transferências e pagamento de fatura.
- **Parcelas**: reconhece “PARC 02/06”, “Parcela 2 de 6” e, em faturas, “LOJA 02/06”. Cria a compra com as parcelas restantes nas próximas faturas.
- O **pagamento da fatura** que aparece no OFX do cartão vem desmarcado.
- Nada é salvo antes da revisão. Na revisão você pode:
  - marcar e desmarcar transações;
  - editar descrição e categoria;
  - escolher quem paga;
  - aplicar pessoas e categorias a todas as transações de uma vez.

## Estrutura do código

```
app/src/main/java/app/financas/
├── domain/          Lógica pura (sem Android), coberta por testes
│   ├── Money, Dates, Billing, Recurrence, Reports
│   └── ofx/         OfxParser, ImportHeuristics, ImportPlanner
├── data/            Room (entidades, DAO, banco), repositório, Snapshot e backup JSON
└── ui/              Compose: tema, componentes, navegação e telas
    ├── home/  transactions/  edit/  reports/  importer/  registry/
```

## Backup

Em **Cadastros › Exportar backup**, todos os dados são salvos em um arquivo `.json` onde você escolher (Drive, Downloads etc.).
**Restaurar backup** substitui tudo pelos dados do arquivo.
