# MaguFinance  
MaguFinanceとはDiscordBOTとして使用できる経済BOTです。  
BOTのTOKENなどは `.env` で、BOTの設定は `bot.properties` で変更できます。  

---

## スラッシュコマンド一覧

## スラッシュコマンド一覧

| コマンド名              | 説明                                | オプション / サブコマンド                                             | 権限               |
|--------------------|-----------------------------------|------------------------------------------------------------|------------------|
| `/stock history`   | 株価の履歴を表示します。                      | `company` *(STRING)* — 特定の会社名を指定できます。                      | なし               |
| `/stock buy`       | 株を購入します。                          | `company` *(STRING)* — 購入する会社<br>`pieces` *(INTEGER)* — 株数 | なし               |
| `/stock sell`      | 株を売却します。                          | `company` *(STRING)* — 売却する会社<br>`pieces` *(INTEGER)* — 株数 | なし               |
| `/stock show`      | 所持している株を全表示します。                   | なし                                                         | なし               |
| `/broad-cast`      | MaguFinanceのお知らせを投稿するチャンネルを登録します。 | `channel` *(CHANNEL)* — チャンネルを指定してください。                    | `MANAGE_CHANNEL` |
| `/atm balance`     | 自分の残高を確認します。                      | サブコマンド: `balance`                                          | なし               |
| `/atm balance-top` | 所持金ランキングを表示します。                   | サブコマンド: `balance-top`                                      | なし               |

---

💡 **補足**  
- `/broad-cast` はサーバー管理者のみ使用可能です。  
- `/atm` コマンド群は銀行システムと連携しており、ユーザーの残高データはDBに保存されます。  
- 追加予定：`/stock-buy`, `/stock-sell` などの売買コマンド。
