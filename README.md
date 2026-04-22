# NetMonitor

<p align="center">
  <b>Android 网络安全监控工具</b><br>
  实时监控设备网络连接 · VPN 抓包分析 · 真实 IP 暴露检测
</p>

---

## 功能特性

- **实时连接监控** — 解析 `/proc/net/tcp|udp` 获取所有活跃网络连接，显示协议、IP、端口、应用归属
- **VPN 数据包捕获** — 本地 VPN 隧道抓包，无需 Root 即可查看所有出入站流量
- **真实 IP 暴露检测** — 自动扫描连接列表，识别 IP 泄漏和端口暴露风险并记录
- **Root 增强模式** — 有 Root 权限时可获取更完整的网络信息（netstat、iptables、ARP 表等）
- **连接筛选过滤** — 按协议、状态、应用名等条件筛选连接和数据包
- **暴露日志持久化** — JSON 文件存储暴露记录，支持导出文本报告
- **系统诊断** — 一键生成设备网络诊断报告（Root 状态、/proc/net 可读性、权限、内存等）

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin 1.9.24 |
| 最低版本 | Android 8.0 (API 26) |
| 目标版本 | Android 15 (API 35) |
| 架构 | MVVM (ViewModel + LiveData + Repository) |
| 异步 | Kotlin Coroutines + SharedFlow |
| 导航 | Jetpack Navigation |
| 构建 | Gradle Kotlin DSL + AGP 8.5.2 |

## 项目结构

```
app/src/main/java/com/netmonitor/app/
├── Constants.kt                    # 全局常量集中管理
├── MainActivity.kt                 # 主活动
├── NetMonitorApp.kt                # Application 初始化
├── model/
│   ├── ConnectionInfo.kt           # 网络连接数据模型
│   ├── ExposureRecord.kt           # 暴露记录数据模型
│   ├── FilterConfig.kt             # 筛选配置
│   └── PacketInfo.kt               # 数据包信息模型
├── repository/
│   └── NetworkRepository.kt        # 数据仓库层
├── receiver/
│   └── BootReceiver.kt             # 开机自启广播
├── service/
│   ├── NetworkMonitorService.kt    # 网络监控前台服务
│   └── PacketCaptureVpnService.kt  # VPN 抓包服务
├── ui/
│   ├── adapter/                    # RecyclerView 适配器
│   ├── connections/                # 连接列表页
│   ├── exposure/                   # 暴露日志页
│   ├── home/                       # 主页总览
│   ├── packets/                    # 抓包列表页
│   └── settings/                   # 设置页
├── util/
│   ├── AppLogger.kt                # 应用日志（环形缓冲区）
│   ├── ExposureLogManager.kt       # 暴露记录管理（线程安全）
│   ├── NetworkParser.kt            # /proc/net 解析器
│   ├── PacketBus.kt                # 数据包事件总线 (SharedFlow)
│   └── RootShell.kt                # Root 命令执行（输入校验）
└── viewmodel/
    └── MonitorViewModel.kt         # 监控 ViewModel（防抖更新）
```

## 构建运行

```bash
# 克隆仓库
git clone https://github.com/521w/NetMonitor.git
cd NetMonitor

# 构建 Debug APK
./gradlew assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 网络访问（VPN 隧道） |
| `ACCESS_NETWORK_STATE` | 获取网络连接状态 |
| `ACCESS_WIFI_STATE` | 获取 WiFi 信息 |
| `ACCESS_FINE_LOCATION` | 获取 WiFi SSID（Android 要求） |
| `FOREGROUND_SERVICE` | 后台持续监控 |
| `POST_NOTIFICATIONS` | 显示监控状态通知 |
| `QUERY_ALL_PACKAGES` | 识别连接所属应用 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |

## 开源协议

MIT License
