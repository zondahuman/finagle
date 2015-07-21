package com.twitter.finagle.stats

import com.twitter.conversions.time._
import com.twitter.util.{Time, MockTimer}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MetricsBucketedHistogramTest extends FunSuite {

  test("basics") {
    if (!sys.props.contains("SKIP_FLAKY")) {
      val timer = new MockTimer()

      Time.withCurrentTimeFrozen { tc =>
        val ps = Array[Double](0.5, 0.9)
        val h = new MetricsBucketedHistogram(
          name = "h",
          percentiles = ps,
          timer = timer)

        def roll(): Unit = {
          tc.advance(61.seconds)
          timer.tick()
        }

        // add some data (A) to the 1st window
        1L.to(100L).foreach(h.add)

        // since we have not rolled to the next window, we should not see that data
        val snap0 = h.snapshot()
        assert(snap0.min() == 0)
        assert(snap0.max() == 0)
        assert(snap0.count() == 0)
        assert(snap0.sum() == 0)
        assert(snap0.avg() == 0)
        assert(snap0.percentiles().map(_.getValue) === Array(0, 0))

        // roll to window 2 (this should make data A visibile)
        roll()

        // add a data point (B) to window 2 (it will not be visible in the snapshot)
        h.add(1000)
        val snap1 = h.snapshot()
        assert(snap1.min() == 1)
        assert(snap1.max() == 100)
        assert(snap1.count() == 100)
        assert(snap1.sum() == 1.to(100).sum)
        assert(snap1.avg() == 50.5d)
        assert(snap1.percentiles().map(_.getValue) === Array(50, 90))

        // fill out this 2nd window (C) and roll the window, we should only see B and C
        1001L.to(10000L).foreach(h.add)
        roll()
        val snap2 = h.snapshot()
        assert(snap2.min() == 1003) // this only needs to be +/- 0.5%
        assert(snap2.max() == 9987) // this only needs to be +/- 0.5%
        assert(snap2.count() == 9001)
        assert(snap2.sum() == 1000L.to(10000L).sum)
        assert(snap2.avg() == 5500.0)
        assert(snap2.percentiles().map(_.getValue) === Array(5498, 9132))

        // roll to the next window, which should evict B and C as well
        roll()
        val snap3 = h.snapshot()
        assert(snap3.min() == 0L)
        assert(snap3.max() == 0L)
        assert(snap3.count() == 0L)
        assert(snap3.sum() == 0L)
        assert(snap3.avg() == 0.0)
        assert(snap3.percentiles().map(_.getValue) === Array(0, 0))

        // add some data (D), roll it into view then confirm clear works
        h.add(1)
        roll()
        assert(h.snapshot().count() == 1L)
        h.clear()
        val snap4 = h.snapshot()
        assert(snap4.min() == 0)
        assert(snap4.max() == 0)
        assert(snap4.count() == 0)
        assert(snap4.sum() == 0)
        assert(snap4.avg() == 0)
        assert(snap4.percentiles().map(_.getValue) === Array(0, 0))
      }
    }
  }
}
