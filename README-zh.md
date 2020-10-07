# HiveMQ-TDengine-Extension 

For English README, please refer to <a href="https://github.com/john-bigz/hivemq-tdengine-extension/blob/master/README.md" >README</a>.

## 1 介绍
HiveMQ-TDengine-Extension是一个HiveMQ extension，可以对发布到特定主题的消息解析并存储到TDengine时序数据库中。
+ 支持 TDengine 2.0 RESTful API 和 JDBC API 进行数据存储。
+ 支持 HiveMQ v4，遵守 HiveMQ extension 开发规范。
+ 支持Docker部署环境。
+ 源代码以Apache License开源。
<br>

## 2 业务场景
本文模拟物联网环境数据采集场景，假设现有一定数据的环境数据采集点，所有采集点数据均通过 MQTT 协议传输至采集平台（MQTT Publish），主题设计如下：
```
application/sensor_data
```
传感器发送的数据格式为 JSON，数据包括时间戳、温度、电压、名称、设备ID等数据。
```json
{
    "ts": 1519833600000, 
    "temperature": 32.1, 
    "voltage": 321, 
    "name": "d02", 
    "devid": 2
}
```
现在需要实时存储以便在后续任意时间查看数据，提出需求：每个设备按照每 5 秒钟一次的频率进行数据上报，数据库需存储每条数据以供后续回溯分析。
<br>
<br>

## 3 环境准备
本文所用的测试环境可以采用 Docker 搭建，需要先安装部署好Docker，有关Docker的安装请参考：https://docs.docker.com/engine/install/。
  
资源与使用教程参照：
  
* HiveMQ CE 社区版：https://www.hivemq.com/

* TDengine：https://www.taosdata.com/cn/
  
* JDK11：https://www.oracle.com/java/technologies/javase-jdk11-downloads.html
 
* Maven：http://maven.apache.org/
<br>

下面是如何构建本地docker镜像并运行的步骤说明：
  
### 3.1 HiveMQ-TDengine-Extension 下载
```
git clone http://github.com/john-bigz/hivemq-tdengine-extension
```
### 3.2 HiveMQ-TDengine-Extension 编译
编译HiveMQ-TDengine-Extension 需要 JDK11和Maven3.6.x，请自行安装。使用下面的编译打包得到tdengine-extension-1.0.0-distribution.zip
```
mvn clean package
```
编译成功后，会得到打包文件：target/tdengine-extension-1.0.0-distribution.zip。
在终端窗口中，转到tdengine-extension代码所在目录下的target子目录， 解压tdengine-extension-1.0.0-distribution.zip:
```
unzip tdengine-extension-1.0.0-distribution.zip
```
### 3.3 下载TDengine安装包
由于准备将TDengine重新部署到本地容器中，需要先下载TDengine安装包到hivemq-tdengine-extension代码所在目录：
```
wget -O ./TDengine-server-2.x-latest-Linux-x64.tar.gz https://www.taosdata.com/download/download-gettingStarted.php\?pkg\=tdengine_tar
```
### 3.4 构建docker本地镜像
通过下面的命令构建docker镜像：
```
docker build -t taosdata-hivemq-extension .
```  
在docker容器中部署运行，并将容器内的服务端口映射到外部:
```
docker run --name hivemq-ce -d -p 1883:1883  -v /etc/taos:/etc/taos -p 6030:6030 -p 6035:6035 -p 6041:6041 -p 6030-6040:6030-6040/udp  taosdata-hivemq-extension
```
  
查看容器实例是否启动成功：
```
  $ docker ps -a
```
<br>
<br>

## 4 测试容器服务
### 4.1 手动测试
* 测试需要使用MQTT发布/订阅工具，以下是在ubuntu下安装mosquitto-clients的命令：
```
sudo apt install mosquitto-clients
``` 
  
* 发布一个测试消息到MQTT服务，主题为application/sensor_data，内容为JSON格式的字符串:
```
mosquitto_pub -h 127.0.0.1 -p 1883 -t 'application/sensor_data' -m '{"ts": 1519833600000, "temperature": 32.1, "voltage": 321, "name": "d02", "devid": 2}'
```

* 通过TDengine的RESTful Connector接口查看测试消息是否已经存储到数据库表中,其中Basic认证字符串"cm9vdDp0YW9zZGF0YQ=="是"root:taosdata"的Base64编码，详见<a href="https://www.taosdata.com/cn/documentation20/connector/#RESTful-Connector">TDengine RESTful Connector</a>：
```
curl -H 'Authorization: Basic cm9vdDp0YW9zZGF0YQ==' -d 'select * from hivemqdb.sensor_data' 127.0.0.1:6041/rest/sql

{"status":"succ","head":["ts","temperature","voltage","name","devid"],"data":[["2018-03-01 00:00:00.000",32.10000,321,"d02",2]],"rows":1}% 
```
如果能从数据表hivqmedb.sensor_data查询到类似上面的数据记录，就说明服务测试通过。
<br>

### 4.2 自动测试
按照 HiveMQ 的 extension进行自动测试。详见：https://www.hivemq.com/docs/hivemq/4.3/extensions/testing-extension.html ，
下面是基本测试步骤：

* 将HiveMQ-TDengine-Extension导入Ideal或者Eclipse。
* 利用Junit5执行`TDengineInterceptorIT` 进行自动化单元测试。
* 该单元测试利用{hivemq-testcontainer}[HiveMQ Testcontainer]将HiveMQ-TDengine-Extension自动打包、部署和运行在一个 HiveMQ docker 容器内。
* 该单元测试会建立一个{hivemq-mqtt-client}[HiveMQ MQTT Client]，发布一个测试消息到主题application/sensor_data，然后利用RESTful接口取回刚写入的记录，并验证记录是否正确。
<br>
<br>   

## 5 配置说明
### 5.1 配置文件：taosdata.properties
所在目录：HiveMQ/extension/tdengine-extension/
```properties
# 模式选择: jdbc or http
mode=jdbc
# MQTT主题
mqtt_topic=application/sensor_data

# 创建TDEngine数据库SQL语句
sql.create_database=create database if not exists hivemqdb;
# 创建TDEngine数据表SQL语句
sql.create_table=create table if not exists hivemqdb.sensor_data (ts timestamp, temperature float, voltage int, name binary(32) ) TAGS (devid int);
# 插入TDEngine数据表SQL语句
sql.insert_table=insert into hivemqdb.sensor_data_${payload.devid} using hivemqdb.sensor_data TAGS (${payload.devid}) VALUES (${payload.ts}, ${payload.temperature}, ${payload.voltage}, '${payload.name}');


# JDBC相关配置参数
jdbc.driverClass=com.taosdata.jdbc.TSDBDriver
jdbc.url=jdbc:TAOS://127.0.0.1:6030/log
jdbc.username=root
jdbc.password=taosdata
jdbc.pool.init=1
jdbc.pool.minIdle=3
jdbc.pool.maxActive=20
jdbc.testSql=select server_status();

# HTTP相关配置参数
http.url=http://127.0.0.1:6041/rest/sql/
http.token=root:taosdata

```
<br>   

### 5.2 模式选择
HiveMQ-TDengine-Extension 支持两种数据存储模式：JDBC和HTTP，其中JDBC模式调用TDengine提供的原生JDBC接口将数据写入数据库表中，而HTTP模式则调用TDengine的RESTful Connector接口。
<br><br>

### 5.3 数据库操作
HiveMQ CE启动加载tdengine-extension时，tdengine-extension将自动执行TDengine数据库表初始化操作：分别执行新建数据库SQL和新建数据表SQL。
参数名|参数说明|默认值
--|:--:|--|
sql.create_database|创建数据库SQL语句|create database if not exists hivemqdb;
sql.create_table|创建数据表SQL语句|create table if not exists hivemqdb.sensor_data (ts timestamp, temperature float, voltage int, name binary(32) ) TAGS (devid int);
sql.insert_table|插入数据表SQL语句|insert into hivemqdb.sensor_data_${payload.devid} using hivemqdb.sensor_data TAGS (${payload.devid}) VALUES (${payload.ts}, ${payload.temperature}, ${payload.voltage}, '${payload.name}');
<br>

### 5.4 JDBC模式
当mode=jdbc时，启用JDBC模式。JDBC模式下，HiveMQ CE启动自动加载tdengine-extension时，首先建立jdbc连接池（依赖<a href="druid.apache.org" target="_blank">Druid</a>）,因此需要配置以下jdbc相关参数：
参数名|参数说明|默认值
--|:--:|--|
jdbc.driverClass|TDengine的JDBC驱动|com.taosdata.jdbc.TSDBDriver
jdbc.url|JDBC URL|jdbc:TAOS://127.0.0.1:6030/log
jdbc.username|JDBC 用户名|root
jdbc.password|JDBC 口令|taosdata
jdbc.pool.init|JDBC 连接池初始连接数|1
jdbc.pool.minIdle|JDBC 连接池最小连接数|1
jdbc.pool.maxActive|JDBC 连接池最大连接数|20
jdbc.testSql|JDBC 连接池保持测试SQL|select server_status();
<br>   

### 5.5 RESTful模式
当mode=http时，启用HTTP模式。HTTP模式下，tdengine-extension调用<a href="httpclient.apache.org" target="_blank">httpclient</a>向TDEngine发送RESTful请求,需要配置以下http相关参数：
参数名|参数说明|默认值
--|:--:|--|
http.url|TDengine RESTful接口URL|http://127.0.0.1:6041/rest/sql/
http.token|TDengine RESTful接口认证信息|root:taosdata
<br>
有关TDengine RESTful接口认证信息，请参考TDengine网站<a href="https://www.taosdata.com/cn/documentation/connector/#RESTful-Connector" target="_blank">RESTful-Connector</a>。
<br><br><br>   
  

## 6 模拟大量数据测试
### 6.1 生成模拟数据的脚本
以下脚本模拟了 10000 个设备在过去 24 小时内、每隔 5 秒钟上报一条模拟数据并发送到 HiveMQ 的场景。

* 总数据量：24 * 3600 / 5 * 10000 = 1.72 亿条

* 消息 TPS：2000

安装 Node.js ，按需修改配置参数后可以通过以下命令启动：
```
1 npm install mqtt mockjs --save --registry=https://registry.npm.taobao.org
2 node mock.js
```
附：模拟生成数据并发送到HiveMQ 代码，请根据实际测试环境调整相关参数
```javascript
// mock.js
const mqtt = require('mqtt')
const Mock = require('mockjs')

const EMQX_SERVER = 'mqtt://localhost:1883'
const CLIENT_NUM = 10000
const STEP = 5000 // 模拟采集时间间隔 ms
const AWAIT = 5000 // 每次发送完后休眠时间，防止消息速率过快 ms
const CLIENT_POOL = []
const BEGIN_TIMESTAMP = 1519833600000;

startMock()


function sleep(timer = 100) {
  return new Promise(resolve => {
    setTimeout(resolve, timer)
  })
}

async function startMock() {
  const now = Date.now()
  for (let i = 0; i < CLIENT_NUM; i++) {
    const client = await createClient(`mock_client_${i}`)
    CLIENT_POOL.push(client)
  }
  let count = 0
  // last 24h every 5s
  const last = 24 * 3600 * 1000
  for (let n = now - last; n <= now; n += STEP) {
    for (const client of CLIENT_POOL) {
      ts = BEGIN_TIMESTAMP + count
      const mockData = generateMockData()
      const data = {
        ...mockData,
        id: client.clientId,
        name: 'D01',
        ts,
      }
      client.publish('application/sensor_data', JSON.stringify(data))
      count++
    }
    const dateStr = new Date(n).toLocaleTimeString()
    console.log(`${dateStr} send success.`)
    await sleep(AWAIT)
  }
  console.log(`Done, use ${(Date.now() - now) / 1000}s, published ${count}`)
}

/**
 * Init a virtual mqtt client
 * @param {string} clientId ClientID
 */
function createClient(clientId) {
  return new Promise((resolve, reject) => {
    const client = mqtt.connect(EMQX_SERVER, {
      clientId,
    })
    client.on('connect', () => {
      console.log(`client ${clientId} connected`)
      resolve(client)
    })
    client.on('reconnect', () => {
      console.log('reconnect')
    })
    client.on('error', (e) => {
      console.error(e)
      reject(e)
    })
  })
}

/**
* Generate mock data
*/
function generateMockData() {
 return {
   "temperature": parseFloat(Mock.Random.float(22, 100).toFixed(2)),
   "voltage": parseFloat(Mock.Random.float(12, 86).toFixed(2)),
   "devid": Mock.Random.integer(0, 20),
 }
}

```
### 6.2 测试结果
以下是模拟数据测试完成后查询TDengine的结果：
```
taos> select count(*) from sensor_data;
       count(*)        |
========================
             172810020 |
Query OK, 1 row(s) in set (0.521210s)

```

### 6.3 问题及解决办法
在运行mock.js进行模拟大量数据测试过程中可能会遇到以下问题，下面提供相应解决办法。
#### 1. "Too many open files"错误
默认情况下linux系统限制了允许打开文件数量，在启用上面的模拟场景测试时如出现"Too many open files"错误，需要修改相关配置参数(以Ubuntu18.04为例)：
* 修改 /etc/security/limits.conf 中如下面这几行：
```
* soft nofile 65535
* hard nofile 65535
```
* 修改 /etc/systemd/user.conf 及 /etc/systemd/system.conf 中如下面这行的配置项：
```
DefaultLimitNOFILE=65535
```
* 这三个地方的文件改好之后，一定要重启系统才得以生效。
<br><br>

#### 2. "TDengine Error: Invalid table ID"错误
```
ERROR - failed in onInboundPublish
java.sql.SQLException: TDengine Error: Invalid table ID
	at com.taosdata.jdbc.TSDBJNIConnector.executeQuery(TSDBJNIConnector.java:131)
	at com.taosdata.jdbc.TSDBStatement.execute(TSDBStatement.java:153)
	at com.alibaba.druid.pool.DruidPooledStatement.execute(DruidPooledStatement.java:633)
	at com.hivemq.extensions.tdengine.TDenginePublishInterceptor.lambda$onInboundPublish$0(TDenginePublishInterceptor.java:107)
	at com.hivemq.extensions.services.executor.WrappedRunnable.run(WrappedRunnable.java:55)
	at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	at java.base/java.lang.Thread.run(Thread.java:834)
```
测试过程中如果出现类似上面的错误，需要在运行测试前，先将相应的表建好，例如：
```
# create super table
create table if not exists hivemqdb.sensor_data (ts timestamp, temperature float, voltage int, name binary(32) ) TAGS (devid int);

# create normal tables
create table if not exists sensor_data_0 using sensor_data tags(0);
create table if not exists sensor_data_1 using sensor_data tags(1);
create table if not exists sensor_data_2 using sensor_data tags(2);
create table if not exists sensor_data_3 using sensor_data tags(3);
create table if not exists sensor_data_4 using sensor_data tags(4);
create table if not exists sensor_data_5 using sensor_data tags(5);
create table if not exists sensor_data_6 using sensor_data tags(6);
create table if not exists sensor_data_7 using sensor_data tags(7);
create table if not exists sensor_data_8 using sensor_data tags(8);
create table if not exists sensor_data_9 using sensor_data tags(9);
create table if not exists sensor_data_10 using sensor_data tags(10);
create table if not exists sensor_data_11 using sensor_data tags(11);
create table if not exists sensor_data_12 using sensor_data tags(12);
create table if not exists sensor_data_13 using sensor_data tags(13);
create table if not exists sensor_data_14 using sensor_data tags(14);
create table if not exists sensor_data_15 using sensor_data tags(15);
create table if not exists sensor_data_16 using sensor_data tags(16);
create table if not exists sensor_data_17 using sensor_data tags(17);
create table if not exists sensor_data_18 using sensor_data tags(18);
create table if not exists sensor_data_19 using sensor_data tags(19);
create table if not exists sensor_data_20 using sensor_data tags(20);

```
详细的TDengine SQL语法说明，请参考<a href="https://www.taosdata.com/cn/documentation/model/#%E5%88%9B%E5%BB%BA%E8%A1%A8">TDengine网站: 数据建模</a>。
<br><br><br>

## 7 设计说明
HiveMQ-TDengine-Extension代码结构相对比较简单，在src/main目录下包含了应用业务处理代码，主要分为三部分：
模块名称|模块说明
--|:--:|
TDengineConfiguration.java | 读取配置文件taosdata.properties并检查其合法性。
TDengineExtensionMain.java | 初始化和安装HiveMQ消息拦截器。
TDenginePulishInterceptor.java | 拦截MQTT消息，并进行消息内容转存到TDengine的处理。
<br>

此外，在src/test目录下包含了单元测试代码，主要分为两部分：
模块名称|模块说明
--|:--:|
TDengineExtensionMainTest.java | 包含针对TDengineExtensionMain的单元测试。
TDengineInterceptoIT.java | 包含针对TDenginePulishInterceptor的单元测试，满足HiveMQ 的 extension 测试要求。
<br>

### 7.1 初始化流程说明
TDengineExtensionMain.java
* 1. 初始化 extension ，读取配置文件 taosdata.properties 。
* 2. 如果配置了 jdbc 模式，则初始化 JDBC 连接池 (Druid)，并利用 JDBC API 执行配置的新建数据库表 SQL 语句。
* 3. 如果配置了 http 模式，则初始化 HTTP 连接池 (HttpClient)， 并利用RESTful API 执行配置的新建数据库表 SQL 语句。
* 4. 安装 MQTT publish 拦截过滤器，初始化处理完成。

<p align="center">
<img src="./extension-init-flowchart.png"/>
</p>


### 7.2 拦截MQTT发布消息保存至TDengine流程说明
TDenginePulishInterceptor.java
* 1. 拦截到一个 MQTT Publish, 检查 topic 和 payload 。
* 2. 如果检查通过，则对 payload 进行 JSON 格式解析，得到插入SQL语句。
* 3. 如果配置了 jdbc 模式，则调用 JDBC API 执行 SQL 插入记录。
* 4. 如果配置了 http 模式，则调用 RESTful API 执行 SQL 插入记录。

<p align="center">
<img src="./extension-work-flowchart.png"/>
</p>


。
