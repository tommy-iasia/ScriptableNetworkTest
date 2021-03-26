# ScriptableNetworkTest (Java6)
This is a batch friendly network speed testing tool.

## Usage
This tool should be used in pair - server mode and client mode.

First, you start the server mode by **server.bat**.
````
java -cp ScriptableNetworkTest-Java6.jar runner.Server 127.0.0.1 34500
````

Then, you start the client mode by **client.bat** with **test.script**
````
java -cp ScriptableNetworkTest-Java6.jar runner.Client 127.0.0.1 34500 test.script
````

![image](https://raw.githubusercontent.com/tommy-iasia/ScriptableNetworkTest-Java6/master/screen.png)

Afterwards, you will see the client connects to server and run the script.

> Usually, server and client being in different machines, change the IP and port from *127.0.0.1:34500* to your needed address.

## Script
````
sleep 500
sync
flood 10000 8190
sync
hold server 1000000
release server 5000
hold client 1000000
release client 5000
sync
flood 10000 8190
sync
````

There are 5 available operations

- `sleep [time/ms]`
- `flood tcp [time/ms] [bufferSize/B] [bandwidth/bps]?` floods the network using TCP stream
- `flood udp [group/host] [port] [time/ms] [bufferSize/B] [bandwidth/bps]?` flods the network using UDP packets
- `sync` synchronize server and client
- `hold [server|client] [memorySize/B]` holds memory to simulate memory consumption cases
- `release [server|client] [memorySize/B]` releases held memory

You are expected to write scripts using the operations with different parameters for different situations.

## Statistics
After flooding the network, statistics will be calculated
````
flood for 10000ms with 8190B buffer...
used 10s / 10001ms
written 11178MB / 11720987460B / 1431kp / 1431134p / 9951ms / 9951797000ns
bandwidth 8941Mbps / 9375852382bps
flood completed
````
It jots down four key values:
- total number of bytes of payloads, in unit B
- number of write actions, in unit p
- total time used for write actions, in unit ns
- calculated bandwidth, in unit bps
