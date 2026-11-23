# 記帳小夥伴

和小夥伴一起記帳：手動記帳、每月固定收支自動入帳、付款通知自動記帳（Android App）、雲端發票手機條碼。

- **網頁版（PWA）**：https://s22643588-sketch.github.io/jizhang/
- **Android App**：到 [Releases](../../releases/latest) 下載 `jizhang.apk`

## 結構
- `index.html`、`sw.js`、`manifest.webmanifest`、`icons/`：畫面與網頁版（App 也用同一份畫面）
- `android/`：Android 原生殼（WebView、通知監聽自動記帳、更新檢查、資料鏡像）
- `.github/workflows/android.yml`：每次推送到 main 自動打包 APK 並發佈到 Releases

## 資料保存
- 記帳資料存在手機本機（App 內 localStorage + App 私有檔案雙份），App 更新不會清掉。
- 解除安裝或換手機前，請在「我的 › 備份與還原」匯出備份。

## 需要的 Secrets
`KEYSTORE_B64`（簽章金鑰 base64）、`KEYSTORE_PASS`（金鑰密碼）。金鑰要一直用同一把，否則手機無法覆蓋更新。
