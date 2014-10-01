package eu.stratosphere.emma.examples.prototype

import eu.stratosphere.emma.api._
import eu.stratosphere.emma.examples.Algorithm
import org.apache.spark.util.Vector

import scala.util.Random

object TranslationPrototype {

  // --------------------------------------------------------------------------------------------
  // ----------------------------------- Schema -------------------------------------------------
  // --------------------------------------------------------------------------------------------

  object Schema {

  }

  /**
   * Temporary, only for debugging.
   *
   */
  def main(args: Array[String]): Unit = {
    val algorithm = new TranslationPrototype(runtime.Native)
    algorithm.run()
  }
}

class TranslationPrototype(rt: runtime.Engine) extends Algorithm(rt) {

  def run() = runGlobalAggregates()

  private def runGlobalAggregates() = {
    val algorithm = /*parallelize*/ {
      val N = 10000
      val A = DataBag(1 to N)

      val B = A.minBy(_ < _)
      val C = A.maxBy(_ < _)
      val D = A.min()
      val E = A.max()
      val F = A.sum()
      val G = A.product()
      val H = A.count()
      val I = A.empty()
      val J = A.exists(_ % 79 == 0)
      val K = A.forall(_ % 79 == 0)
    }

    //algorithm.run(rt)
  }

  private def runTest() = {
    val algorithm = /*parallelize*/ {
      val N = 10000

      val A = for (a <- DataBag(1 to N)) yield (a, 2 * a, 3 * a)

      A.fetch()
    }

    //algorithm.run(rt)
  }

  private def runMinimal() = {
    val algorithm = /*parallelize*/ {
      val N = 10000
      val M = Math.sqrt(N).ceil.toInt

      val X = DataBag(1 to M)
      val Y = DataBag(1 to M)
      val Z = DataBag(1 to N)

      val A = for (x <- X; y <- Y; z <- Z; if x * x + y * y == z) yield (x, y, z)
    }

    //algorithm.run(rt)
  }

  private def runCompareStoreSales() = {

    val salesLUrl = "file://tmp/cmp-sales-input-l.txt"
    val salesRUrl = "file://tmp/cmp-sales-input-r.txt"
    val outputUrl = "file://tmp/cmp-sales-output.txt"

    import eu.stratosphere.emma.examples.exploration.unnesting.CompareStoreSales.Schema._

    val algorithm = /* parallelize */ {

      val salesL = read(salesLUrl, new InputFormat[SalesHistory])
      val salesR = read(salesRUrl, new InputFormat[SalesHistory])

      val comparison = for (l <- salesL.groupBy(x => GroupKey(x.store, x.date));
                            r <- salesR.groupBy(x => GroupKey(x.store, x.date));
                            if l.key.store.area == r.key.store.area && l.key.date == r.key.date) yield {

        val balance = for (entryL <- l.values;
                           entryR <- r.values;
                           if entryL.product == entryR.product) yield entryL.count * entryL.product.price - entryR.count * entryR.product.price

        SalesBalance(l.key.store, r.key.store, l.key.date, balance.sum())
      }

      write(outputUrl, new OutputFormat[SalesBalance])(comparison)
    }

    // algorithm.run(rt)
  }

  private def runKMeans() = {

    val epsilon = 0.5
    val k = 3
    val inputUrl = "file://tmp/kmeans-input.txt"
    val outputUrl = "file://tmp/kmeans-output.txt"

    import eu.stratosphere.emma.examples.datamining.clustering.KMeans
    import eu.stratosphere.emma.examples.datamining.clustering.KMeans.Schema._

    val algorithm = parallelize {
      // read input
      val points = read(inputUrl, new InputFormat[Point])

      // initialize random cluster means
      val random = new Random(KMeans.SEED)
      var means = DataBag(for (i <- 1 to k) yield Point(i, Vector(random.nextDouble(), random.nextDouble(), random.nextDouble())))
      var change = 0.0

      // initialize solution
      var solution = for (p <- points) yield {
        val closestMean = means.minBy((m1, m2) => (p.pos squaredDist m1.pos) < (p.pos squaredDist m2.pos)).get
        Solution(p, closestMean.id)
      }

      do {
        // re-compute means
        val newMeans = for (cluster <- solution.groupBy(_.clusterID)) yield {
          val sum = (for (p <- cluster.values) yield p.point.pos).fold[Vector](Vector.zeros(3), identity, (x, y) => x + y)
          val cnt = (for (p <- cluster.values) yield p.point.pos).fold[Int](0, _ => 1, (x, y) => x + y)
          Point(cluster.key, sum / cnt)
        }

        change = {
          val distances = for (mean <- means; newMean <- newMeans; if mean.id == newMean.id) yield mean.pos squaredDist newMean.pos
          distances.sum()
        }

        means = newMeans

        // update solution: re-assign clusters
        solution = for (s <- solution) yield {
          val closestMean = means.minBy((m1, m2) => (s.point.pos squaredDist m1.pos) < (s.point.pos squaredDist m2.pos)).get
          s.copy(clusterID = closestMean.id)
        }

      } while (change < epsilon)

      // write result
      write(outputUrl, new OutputFormat[(PID, PID)])(for (s <- solution) yield (s.point.id, s.clusterID))
    }

    algorithm.run(rt)
  }
}