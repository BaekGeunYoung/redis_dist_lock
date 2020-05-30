import org.redisson.Redisson
import org.redisson.config.Config
import java.util.concurrent.TimeUnit

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