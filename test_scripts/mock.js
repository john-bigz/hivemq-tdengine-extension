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
