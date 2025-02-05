package com.example.crud

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.crud.ui.theme.CrudTheme
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import kotlin.random.Random

// Modelo de datos
data class Producto(val id: Int, val nombre: String, val precio: Double)

// Retrofit API
interface ApiService {
    @GET("api/productos")
    fun getProductos(): Call<List<Producto>>

    @POST("api/productos")
    fun createProducto(@Body producto: Producto): Call<Producto>
}

object RetrofitInstance {
    private const val BASE_URL = "https://api-movile.onrender.com/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CrudTheme {
                ProductScreen()
            }
        }
    }
}

@Composable
fun ProductScreen() {
    var productos by remember { mutableStateOf<List<Producto>>(emptyList()) }
    var nombreProducto by remember { mutableStateOf(TextFieldValue("")) }
    var precioProducto by remember { mutableStateOf(TextFieldValue("")) }

    // Obtener productos de la API
    LaunchedEffect(Unit) {
        val call = RetrofitInstance.api.getProductos()
        call.enqueue(object : Callback<List<Producto>> {
            override fun onResponse(call: Call<List<Producto>>, response: Response<List<Producto>>) {
                if (response.isSuccessful) {
                    productos = response.body() ?: emptyList()
                } else {
                    Log.e("ProductScreen", "Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {
                Log.e("ProductScreen", "Error de red: ${t.message}")
            }
        })
    }

    // Interfaz principal
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("Gestión de Productos") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Formulario para agregar producto
            TextField(
                value = nombreProducto,
                onValueChange = { nombreProducto = it },
                label = { Text("Nombre del Producto") },
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = precioProducto,
                onValueChange = { precioProducto = it },
                label = { Text("Precio del Producto") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val nuevoProducto = Producto(
                        id = Random.nextInt(1000),
                        nombre = nombreProducto.text,
                        precio = precioProducto.text.toDoubleOrNull() ?: 0.0
                    )
                    createProducto(nuevoProducto) {
                        productos = productos + it
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Agregar Producto")
            }

            // Lista de productos
            Text("Lista de Productos", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(productos) { producto ->
                    ProductoItem(producto)
                }
            }
        }
    }
}

// Crear un producto usando la API
fun createProducto(producto: Producto, onSuccess: (Producto) -> Unit) {
    val call = RetrofitInstance.api.createProducto(producto)
    call.enqueue(object : Callback<Producto> {
        override fun onResponse(call: Call<Producto>, response: Response<Producto>) {
            if (response.isSuccessful) {
                response.body()?.let { onSuccess(it) }
            } else {
                Log.e("ProductScreen", "Error al crear producto: ${response.code()}")
            }
        }

        override fun onFailure(call: Call<Producto>, t: Throwable) {
            Log.e("ProductScreen", "Error de red: ${t.message}")
        }
    })
}

// Composable para mostrar un producto
@Composable
fun ProductoItem(producto: Producto) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "ID: ${producto.id}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Nombre: ${producto.nombre}", style = MaterialTheme.typography.bodyLarge)
            Text(text = "Precio: ${producto.precio}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
