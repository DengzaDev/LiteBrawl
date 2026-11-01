LiteBrawl 测试服务器
====================

这是一个开箱即用的 Paper 1.21.1 测试服，里面已经装好了 LiteBrawl 插件。
不需要再下载任何插件，解压后按下面三步就能进游戏。

需要 Java 21（https://adoptium.net）。


第一步：同意 EULA
-----------------
打开 eula.txt，把

    eula=false

改成

    eula=true

（这是 Mojang 的要求，必须由你自己同意：https://aka.ms/MinecraftEULA）


第二步：启动
-----------
Windows：双击 start.bat
Linux / macOS：./start.sh

首次启动会生成世界，大约 30~60 秒。
出现 "Done (...)" 就说明开好了。


第三步：进游戏
-------------
在 Minecraft 里添加服务器：地址 127.0.0.1:25565

本包的 server.properties 里 online-mode=false（离线模式），
所以不需要正版账号也能进，方便本地测试。


网页控制台（推荐先用这个）
-------------------------
启动完成后，控制台会打印一行类似的地址：

    http://127.0.0.1:8787/?token=xxxxxxxx

复制到浏览器打开，里面有开服向导、60 多项可视化配置、
YAML 编辑器、数据库浏览和实时状态。所有配置都能在网页里改。


开局三步（进服前要先建地图）
---------------------------
插件不会凭空生成地图，需要把服务器目录里真实存在的世界注册进来。
控制台（或游戏内管理员）依次执行：

    /maps create world 测试地图
    /map setspawn
    /map scan

说明：
- /map setspawn 需要站在想要的出生点上执行（游戏内）
- /map scan 只读取地图里已有的绿宝石方块，不会放置任何方块
- 扫描完可以用 /lb status 确认「出生方块」数量大于 0

之后玩家进服会拿到大厅物品，右键「开始游戏」即可进场。


常见问题
--------
Q: 双击 start.bat 一闪而过？
   Java 没装好，或没装 Java 21。在命令行执行 java -version 看看。

Q: 提示端口被占用？
   已经有别的服务端在跑。改 server.properties 里的 server-port。

Q: 假人不出现？
   假人是僵尸，世界难度必须是 easy 或更高（本包已设为 easy）。
   如果你把 difficulty 改成了 peaceful，僵尸会被立刻清除。

Q: 换了新版本的插件 jar，但行为没变？
   Paper 会缓存 remap 后的插件。删掉 plugins/.paper-remapped/ 目录再启动。

Q: 网页控制台打不开？
   默认只监听 127.0.0.1，只能在本机访问。地址里的 token 要和控制台打印的一致。


更多文档
--------
https://github.com/DengzaDev/LiteBrawl
