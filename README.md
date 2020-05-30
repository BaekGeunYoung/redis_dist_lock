# distributed lock

여러 독립된 프로세스에서 하나의 자원을 공유해야 할 때, 데이터에 결함이 발생하지 않도록 하기 위해서 분산 락을 활용할 수 있다. 분산 락을 구현하기 위해서는 데이터베이스 등 여러 프로세스가 공통으로 사용하는 저장소를 활용해야 하는데, 이번 실습에서는 레디스를 이용해 분산 락을 활용하는 방법을 학습해보았다.

## Redlock

https://redis.io/topics/distlock

redis에서는 분산 락을 구현한 알고리즘으로 redlock이라는 것을 제공하고 있다. 이 redlock은 다양한 플랫폼에서 구현되어 있는데, 자바에서의 redlock 구현체는 redisson이라고 한다. 이 redisson을 이용해 분산 락을 활용할 것이며, 정보의 결함 없이 공유 자원 관리가 잘 되는지 확인해 볼 것이다.

## Code

`Main.kt`
```kotlin
fun main() {
    val config = Config()
    config.useSingleServer().address = "redis://localhost:6379"
    val redissonClient = Redisson.create(config)

    val start = System.currentTimeMillis()
    for (i in (0 until 10000)) {
        val lock = redissonClient.getLock("myLock")
        if (lock.tryLock(1000, 1000, TimeUnit.MILLISECONDS)) {
            try {
                val value = redissonClient.getAtomicLong("key")
                println(value.incrementAndGet())
            } finally {
                lock.unlock()
            }
        }
    }

    val end = System.currentTimeMillis()
    println("result : ${redissonClient.getAtomicLong("key").get()}")
    println("elapsed time : ${end - start}ms")
}
```

### 코드가 하는 일

- "mylock"이라는 이름의 lock의 획득을 시도한다.
- lock을 획득하면 공유 자원을 얻어 값을 1 증가시킨다.
- 작업이 끝나면 획득했던 lock을 해제한다.
- 위 작업을 10000번 반복한다.

#### lock의 획득

lock.tryLock() 함수는 wait time과 lease time을 각각 첫 번째, 두 번째 파라미터로 전달 받는다. wait time 동안 lock의 획득을 시도하며, 이 시간이 초과되면 lock의 획득에 실패하고 tryLock 함수는 false를 리턴한다. lock의 획득에 성공한 이후엔 lease time이 지나면 자동으로 lock을 해제한다.

## 테스트

동시에 2개의 프로세스를 돌리고, 결과적으로 redis에 값이 원하는대로 잘 저장되었는지 확인해보았다. 10000번 값을 증가시키는 프로세스가 2개 있으므로 결과적으로 데이터의 값은 20000이 되어야 한다.

```
# redis-cli
127.0.0.1:6379> get key
"19946"
```

프로세스 실행이 끝난 후 redis-cli를 통해 값을 확인해본 결과 20000이 아니라 19946이 되어 있었다. 그 이유는 tryLock의 wait time을 100ms로 설정해주었는데, 이 시간 동안 lock을 획득하지 못해서 로직을 수행하지 못하고 생략된 부분이 존재하기 때문인 것으로 생각했다. wait time을 100ms 에서 1000ms로 늘리고 다시 테스트를 해보았다.

```
# redis-cli
127.0.0.1:6379> get key
"20000"
```

이번엔 의도한대로 값이 저장되었다. 추가로 프로세스를 3개로 하여 테스트를 해보았을 때도, 결과 값이 30000으로 잘 저장되는 것을 확인할 수 있었다.

```
# redis-cli
127.0.0.1:6379> get key
"30000"
```

## 결론

redisson에서 제공하는 분산 락 기능을 활용하여 여러 프로세스에 걸친 공유 자원 관리 방법을 학습해보았다. 전에 진행했었던 [티켓 예매 서버 구현](https://github.com/BaekGeunYoung/stress-ticket-reservation-worker) 프로젝트에서는 queue consumer가 하나뿐이어서 실제로 성능이 좋지 않을 것이라는 결함이 있었는데, consumer의 개수를 늘리고 분산 락을 통해 공유 자원을 관리하면 좋을 것 같다.

추가로, wait time과 lease time을 어떤 상황에서 얼만큼으로 설정해야 적절한지에 대해서는 좀 더 학습이 필요할 것 같다.