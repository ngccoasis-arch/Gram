# Matched Media and Transfer Benchmarks

No device results are recorded yet. Results must be measured on the same POCO F7 Pro, account, network, files, thermal state, and test order. A non-Premium slowdown shared by all clients is an observed Telegram/account constraint, not a Gram regression.

## Test controls

- Record Android/HyperOS build, client versions, Telegram account tier, network, signal, VPN/proxy, free storage, and battery mode.
- Cool the device and randomize client order between trials.
- Use the same private messages/file IDs where practical; run at least five trials and compare medians plus failures.
- Separate cold-cache from warm-cache results. Record whether each file had crossed 1 GB cumulative account transfer.

## Results template

| Metric | Conditions | Official Telegram | Plus Messenger | Gram | Notes |
|---|---|---:|---:|---:|---|
| Viewer first frame | cold photo | — | — | — | ms |
| Adjacent photo | original uncached | — | — | — | ms |
| Video first frame | prefix unavailable | — | — | — | ms |
| Buffered seek | +60 seconds | — | — | — | ms |
| Remote seek | +60 seconds | — | — | — | ms |
| Visible jank | 60-second playback | — | — | — | frames / % |
| Throughput | one file | — | — | — | MB/s |
| Throughput | 2 concurrent | — | — | — | aggregate MB/s |
| Throughput | 4 concurrent | — | — | — | aggregate MB/s |
| Throughput | 8 concurrent | — | — | — | aggregate MB/s |

## Interpretation

Gram can improve scheduling, avoid self-imposed throttles, prioritize visible byte ranges, reduce redundant cancellation, tune concurrency, keep app-private writes efficient, and cooperate with Android background execution. It cannot override Telegram datacenter/account policy, Premium entitlements, CDN availability, ISP conditions, radio limits, device thermals, decoder capability, or TDLib behaviors not exposed through its public API.
