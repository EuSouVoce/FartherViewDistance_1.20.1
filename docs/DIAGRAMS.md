# FartherViewDistance — Mermaid Diagrams

These diagrams explain how the plugin works, from a server owner’s view to a deep technical view.

---

## 1) “What happens when the plugin runs?”

```mermaid
flowchart TD
    A[Server starts] --> B[Plugin enabled]
    B --> C{Minecraft version supported?}
    C -- No --> D[Plugin disables itself]
    C -- Yes --> E[Loads config.yml]
    E --> F[Starts view-distance system]
    F --> G[Players get extended view distance]
    G --> H[Chunks sent to players]
    H --> I[Network limits respected]
    I --> J["Reports + metrics (optional)"]

    style D fill:#fdd,stroke:#f66,stroke-width:1px
```

**What you can control:**
- View distance mode and limits (config.yml).
- Per-world limits and safety caps (config.yml).
- Whether metrics (bStats) are enabled.

---

## 2) Startup lifecycle

```mermaid
sequenceDiagram
    participant Server as Bukkit/Paper
    participant Plugin as ChunkIndex
    participant Config as ConfigData
    participant ChunkServer as ChunkServer
    participant Events as ChunkEvent

    Server->>Plugin: onEnable()
    Plugin->>Config: load config.yml
    Plugin->>Plugin: validate MC version
    alt Supported
        Plugin->>ChunkServer: new ChunkServer(config, viewShape, branches)
        Plugin->>ChunkServer: initView(players online)
        Plugin->>ChunkServer: initWorld(worlds)
        Plugin->>Events: register listeners
        Plugin->>Plugin: register commands
        Plugin->>Plugin: start metrics (if enabled)
    else Unsupported
        Plugin-->>Server: disable plugin
    end
```

---

## 3) Runtime ticks + threads

```mermaid
flowchart TD
    subgraph Main_Thread["Main Thread (Global scheduler)"]
        S1["tickSync() every tick"] --> S2[run sync queue]
    end

    subgraph Async_Tasks[Async Scheduler]
        A1["tickAsync() every 50 milliseconds (1 tick)"] --> A2[reset network counters]
        A3["tickReport() every second (20 ticks)"] --> A4[roll up stats]
    end

    subgraph Custom_Threads[Custom threads]
        V1[View thread] --> V2[update view distance]
        T1[Worker threads] --> T2[move players / pick chunks / send]
    end

    S2 --> V2
    V2 --> T2
```

---

## 4) Chunk sending pipeline

```mermaid
flowchart TD
    A[Worker thread loop] --> B[Pick player + next chunk]
    B --> C{Traffic limits OK?}
    C -- No --> B
    C -- Yes --> D{Fast path enabled?}

    D -- Yes --> E{Chunk in memory cache?}
    E -- Yes --> F[Build NBT + light]
    E -- No --> G{Chunk NBT on disk?}
    G -- Yes --> F
    G -- No --> H[Async load/generate chunk]
    H --> F

    D -- No --> H

    F --> I[PlayerSendExtendChunkEvent]
    I --> J{Cancelled?}
    J -- Yes --> B
    J -- No --> K[Optional anti-xray rewrite]
    K --> L[Send chunk + light packet]
    L --> M[Update traffic + metrics]
    M --> B
```

**Gatekeepers before a chunk is processed**
- `ChunkServer.runThread` reevaluates `globalPause`, the global/world/player traffic counters, generation caps, `view.waitSend`, and the `PlayerChunkView` `moveTooFast` flag before it hands a chunk key to the pipeline.
- The view keeps per-player pacing via `PlayerChunkView.waitSend`, `delay()`/`delayTime`, and `next()`, which also honors the player's world border, `syncKey`, and forced delays so stale or out-of-range chunks never make it into the fast path.

---

## 5) Packet & event interception

```mermaid
sequenceDiagram
    participant Player
    participant BranchPacket
    participant ChunkEvent
    participant ChunkServer

    BranchPacket->>ChunkEvent: PacketMapChunkEvent
    ChunkEvent->>ChunkServer: packetEvent(player, event)
    ChunkServer->>ChunkServer: mark chunk as sent

    BranchPacket->>ChunkEvent: PacketViewDistanceEvent
    ChunkEvent->>ChunkEvent: cancel if mismatch with extendDistance

    BranchPacket->>ChunkEvent: PacketUnloadChunkEvent
    ChunkEvent->>ChunkEvent: cancel unload if chunk was just sent
```

---

## 6) View-distance computation

```mermaid
flowchart TD
    A["PlayerChunkView.updateDistance()"] --> B[Compute max allowed distance]
    B --> C[Ensure >= server view distance]
    C --> D[Mark wait/sent map]
    D --> E[Fire PlayerSendViewDistanceEvent]
    E --> F{Cancelled?}
    F -- No --> G[sendViewDistance packet]
    F -- Yes --> H[Do nothing]
```

---

## 7) Config impacts (simple)

```mermaid
flowchart TD
    A[config.yml] --> B[View distance mode]
    A --> C[Server cap]
    A --> D[Per-world cap]
    A --> E[Network limit per player]
    A --> F[Async thread count]

    B --> G[ChunkServer behavior]
    C --> G
    D --> G
    E --> G
    F --> G
    G --> H[How many chunks are sent]
    G --> I[How fast chunks are sent]
```

---

## 8) Metrics (optional)

```mermaid
flowchart LR
    A[Metrics enabled?] -->|No| B[Skip bStats]
    A -->|Yes| C[MetricsCollector]
    C --> D[Custom charts]
    D --> E[bStats backend]
```

---

## 9) Packet proxy + routing

```mermaid
sequenceDiagram
    participant Net as Netty/NMS pipeline
    participant Proxy as ProxyPlayerConnectionCode
    participant Events as Bukkit Events
    participant ChunkEvent as ChunkEvent
    participant ChunkServer as ChunkServer

    Note over Net,Proxy: Outgoing packets (server -> player)
    Net->>Proxy: write(packet)
    alt ClientboundLevelChunkWithLightPacket
        Proxy->>Events: PacketMapChunkEvent
        Events->>ChunkEvent: on(PacketMapChunkEvent)
        ChunkEvent->>ChunkServer: packetEvent(player, event)
        ChunkServer->>ChunkServer: mark chunk as sent
        Proxy-->>Net: allow send
    else ClientboundForgetLevelChunkPacket
        Proxy->>Events: PacketUnloadChunkEvent
        Events->>ChunkEvent: on(PacketUnloadChunkEvent)
        ChunkEvent->>ChunkEvent: cancel if chunk recently sent
        Proxy-->>Net: allow or cancel
    else ClientboundSetChunkCacheRadiusPacket
        Proxy->>Events: PacketViewDistanceEvent
        Events->>ChunkEvent: on(PacketViewDistanceEvent)
        ChunkEvent->>ChunkEvent: cancel if mismatch with extendDistance
        Proxy-->>Net: allow or cancel
    else Other packet
        Proxy-->>Net: allow send
    end

    Note over Net,Proxy: Incoming packets (player -> server)
    Net->>Proxy: read(packet)
    alt ServerboundKeepAlivePacket
        Proxy->>Events: PacketKeepAliveEvent
        Events->>ChunkEvent: on(PacketKeepAliveEvent)
        ChunkEvent->>ChunkEvent: update network speed
        Proxy-->>Net: allow or cancel
    else Other packet
        Proxy-->>Net: allow
    end
```

**Keep-alive-based network timing**
- `ChunkEvent.on(PacketKeepAliveEvent)` cancels keep-alive responses that match the view's pending ping or speed IDs so the plugin can measure the round-trip and throughput without spamming the client.
- `ChunkServer.sendChunk` consumes those measurements via `PlayerChunkView.networkSpeed` before and after each chunk send, which lets the worker threads enforce the auto-adaptive per-player bandwidth limits before emitting more data.

---

## 10) Anti-overload logic: “How it avoids too many chunk ticks”

```mermaid
flowchart TD
    A[Worker thread loop] --> B[Pick next player]
    B --> C{Global pause?}
    C -- Yes --> B
    C -- No --> D{Server traffic limit ok?}
    D -- No --> B
    D -- Yes --> E{World traffic limit ok?}
    E -- No --> B
    E -- Yes --> F{Player traffic limit ok?}
    F -- No --> B
    F -- Yes --> G{Player moving too fast?}
    G -- Yes --> B
    G -- No --> H{Player waitSend?}
    H -- Yes --> B
    H -- No --> I[Pick next chunk]
    I --> J{Fast path enabled?}
    J -- Yes --> K[Try memory cache]
    K --> L{Hit?}
    L -- Yes --> P[Send chunk + light]
    L -- No --> M[Try disk NBT]
    M --> N{Hit?}
    N -- Yes --> P
    N -- No --> O[Async load or generate]
    O --> P
    J -- No --> O
    P --> Q[Update traffic + reports]
    Q --> B
```

---

## 11) Chunk generation safety caps (server + world)

```mermaid
flowchart TD
    A[Need chunk not cached] --> B{Can generate?}
    B -->|No| C[Skip/remove chunk request]
    B -->|Yes| D[Increment server generate count]
    D --> E[Increment world generate count]
    E --> F[Load/generate chunk async]
    F --> G[Send chunk + light]
```

---

## 12) View install/unload cycle (why it avoids heavy re-sends)

```mermaid
flowchart TD
    A[Player teleports or moves far] --> B["unloadView()"]
    B --> C[send view distance 0]
    C --> D[clear view map]
    D --> E[Player settles]
    E --> F["install()"]
    F --> G[recalculate distance]
    G --> H[send view distance]
```
