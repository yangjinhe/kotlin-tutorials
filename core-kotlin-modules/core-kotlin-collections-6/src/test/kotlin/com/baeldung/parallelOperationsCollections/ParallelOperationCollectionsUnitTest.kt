package com.baeldung.parallelOperationsCollections

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.toObservable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.stream.Collectors


class ParallelOperationCollectionsUnitTest {

    private val logger = LoggerFactory.getLogger("")

    data class Person(val name: String, val age: Int, var isAdult: Boolean? = null)

    private val people = listOf(
        Person("Martin", 12),
        Person("Ahmad", 42),
        Person("Alina", 13),
        Person("Alice", 30),
        Person("Bob", 16),
        Person("Charlie", 40)
    )

    private fun List<Person>.assertOver15AndSortedByAge() {
        assertThat(this).containsExactly(
            Person("Bob", 16, false),
            Person("Alice", 30, true),
            Person("Charlie", 40, true),
            Person("Ahmad", 42, true)
        )
    }

    private fun Person.setAdult() {
        this.isAdult = this.age >= 18
        logger.info(this.toString())
    }

    private fun Instant.printTotalTime() {
        val totalTime = Duration.between(this, Instant.now()).toMillis()
        logger.info("Total time taken: {} ms", totalTime)
    }

    @Test
    fun `using coroutines for parallel operations`() = runBlocking {
        logger.info("Using Coroutines")
        val startTime = Instant.now()

        val filteredPeople = people
            .map { person ->
                async(Dispatchers.IO) {
                    person.setAdult()
                    person
                }
            }.awaitAll()
            .filter { it.age > 15 }
            .sortedBy { it.age }

        startTime.printTotalTime()

        filteredPeople.assertOver15AndSortedByAge()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `using coroutines for parallel operations with Flow`() = runBlocking {
        logger.info("Using Kotlin Flow")
        val startTime = Instant.now()

        val filteredPeople = people.asFlow()
            .flowOn(Dispatchers.IO)
            .flatMapMerge { person ->
                flow {
                    person.setAdult()
                    emit(person)
                }
            }
            .filter { it.age > 15 }.toList()
            .sortedBy { it.age }

        startTime.printTotalTime()

        filteredPeople.assertOver15AndSortedByAge()
    }

    @Test
    fun `using RxJava for parallel operations`() { // Observable.class from io.reactivex;
        logger.info("Using RxJava")
        val startTime = Instant.now()

        val observable = Observable.fromIterable(people)
            .flatMap(
                {
                    Observable.just(it)
                        .subscribeOn(Schedulers.computation())
                        .doOnNext { person ->
                            person.setAdult()
                        }
                }, people.size // Uses maxConcurrency for the number of elements
            )
            .filter { it.age > 15 }
            .toList()
            .map { it.sortedBy { person -> person.age } }
            .blockingGet()

        startTime.printTotalTime()

        observable.assertOver15AndSortedByAge()
    }

    @Test
    fun `using RxKotlin for parallel operations`() { // ObservableKt.kt.class from io.reactivex.rxkotlin
        logger.info("Using RxKotlin")
        val startTime = Instant.now()

        val observable = people.toObservable()
            .flatMap(
                {
                    Observable.just(it)
                        .subscribeOn(Schedulers.computation())
                        .doOnNext { person ->
                            person.setAdult()
                        }
                }, people.size // Uses maxConcurrency for the number of elements
            ).filter { it.age > 15 }
            .toList()
            .map { it.sortedBy { person -> person.age } }
            .blockingGet()

        startTime.printTotalTime()

        observable.assertOver15AndSortedByAge()
    }

    @Test
    fun `using parallelStream()`() {
        logger.info("Using Stream API")
        val startTime = Instant.now()

        val filteredPeople = people.parallelStream()
            .map { person ->
                person.setAdult()
                person
            }.filter { it.age > 15 }
            .sorted { p1, p2 -> p1.age.compareTo(p2.age) }
            .collect(Collectors.toList())

        startTime.printTotalTime()

        filteredPeople.assertOver15AndSortedByAge()
    }

    @Test
    fun `using ExecutorService for parallel operations`() {
        logger.info("Using ExecutorService")
        val startTime = Instant.now()

        val executor = Executors.newFixedThreadPool(people.size)
        val futures: List<Future<Person>> = people
            .map { person ->
                executor.submit(Callable {
                    person.setAdult()
                    person
                })
            }

        val results = futures
            .map { it.get() }
            .filter { it.age > 15 }
            .sortedBy { it.age }

        executor.shutdown()

        startTime.printTotalTime()

        results.assertOver15AndSortedByAge()
    }

}

