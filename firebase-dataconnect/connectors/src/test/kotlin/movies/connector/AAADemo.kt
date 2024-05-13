package movies.connector

suspend fun run() {
  val connector = MoviesConnector.instance

  val addMovieResult1 = connector.addMovie.execute(title="The Phantom Menace") {
    director = "George Lucas"
  }
  val empireStrikesBack = connector.getMovieByKey.execute(addMovieResult1.data.key)
  println("Empire Strikes Back: ${empireStrikesBack.data.movie}")

  val addMovieResult2 = connector.addMovie.execute(title="Attack of the Clones") {
    director = "George Lucas"
  }

  val listMoviesResult = connector.listMovies.execute(director = "George Lucas")
  println(listMoviesResult.data.movies)

  connector.listMovies.flow(director = "George Lucas").collect {
    println(it.movies)
  }

  connector.addMovie.execute(title="A New Hope") {
    director = "George Lucas"
  }
  connector.listMovies.execute(director = "George Lucas") // will cause the Flow to get notified
}