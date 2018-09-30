package helpers

import java.util.concurrent.ConcurrentHashMap

import scala.util.Random

object PortManager {

  private val set: ConcurrentHashMap.KeySetView[Int, java.lang.Boolean] =
    ConcurrentHashMap.newKeySet[Int]()

  def allocate(): Int = synchronized {
    def next = 40000 + Random.nextInt(10000)
    var v    = next
    while (set.contains(v)) {
      v = next
    }
    set.add(next)
    v
  }

  def deallocate(v: Int): Boolean = set.remove(v)

}
