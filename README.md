# Volatile Stocks Android Pro

This project upgrades the scanner into a more complete day-trading helper:

- Alpha Vantage scanner with filters for price, volume, and daily change
- Sorting by score, change, volume, or price
- Stock detail screen with intraday chart and RSI
- Local notification alerts for sharp drops and near-high conditions
- GitHub Actions workflow to build a debug APK

## Setup

1. Open `MainActivity` in the app and paste your Alpha Vantage API key into the field.
2. Run a scan.
3. Tap a stock card to open the detail screen.
4. Save alert thresholds in the detail screen.

## Important note

The app refreshes the detail screen every 15 seconds **while that screen is open**. This is intended for active use, not continuous background streaming.
