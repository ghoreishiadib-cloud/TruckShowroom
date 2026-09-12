package com.truckshowroom.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore by preferencesDataStore("truck_showroom")

data class Truck(
    val id: String = UUID.randomUUID().toString(),
    val stockNo: String = "",
    val type: String = "کشنده",
    val brand: String = "",
    val model: String = "",
    val year: String = "",
    val mileage: String = "",
    val engine: String = "",
    val transmission: String = "",
    val axle: String = "",
    val cabin: String = "",
    val fuel: String = "",
    val price: String = "",
    val status: String = "موجود",
    val description: String = "",
    val photos: List<String> = emptyList()
)

data class Customer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = "",
    val budget: String = "",
    val interest: String = "",
    val notes: String = ""
)

class LocalStore(private val context: Context) {
    private val trucksKey = stringPreferencesKey("trucks")
    private val customersKey = stringPreferencesKey("customers")

    suspend fun loadTrucks(): List<Truck> {
        val raw = context.dataStore.data.first()[trucksKey] ?: "[]"
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { truckFromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    suspend fun saveTrucks(list: List<Truck>) {
        val a = JSONArray()
        list.forEach { a.put(truckToJson(it)) }
        context.dataStore.updateData { it.toMutablePreferences().apply { set(trucksKey, a.toString()) } }
    }

    suspend fun loadCustomers(): List<Customer> {
        val raw = context.dataStore.data.first()[customersKey] ?: "[]"
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { customerFromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    suspend fun saveCustomers(list: List<Customer>) {
        val a = JSONArray()
        list.forEach { a.put(customerToJson(it)) }
        context.dataStore.updateData { it.toMutablePreferences().apply { set(customersKey, a.toString()) } }
    }

    private fun truckToJson(t: Truck) = JSONObject().apply {
        put("id", t.id); put("stockNo", t.stockNo); put("type", t.type); put("brand", t.brand)
        put("model", t.model); put("year", t.year); put("mileage", t.mileage); put("engine", t.engine)
        put("transmission", t.transmission); put("axle", t.axle); put("cabin", t.cabin)
        put("fuel", t.fuel); put("price", t.price); put("status", t.status)
        put("description", t.description); put("photos", JSONArray(t.photos))
    }

    private fun truckFromJson(o: JSONObject) = Truck(
        id=o.optString("id"), stockNo=o.optString("stockNo"), type=o.optString("type"),
        brand=o.optString("brand"), model=o.optString("model"), year=o.optString("year"),
        mileage=o.optString("mileage"), engine=o.optString("engine"), transmission=o.optString("transmission"),
        axle=o.optString("axle"), cabin=o.optString("cabin"), fuel=o.optString("fuel"),
        price=o.optString("price"), status=o.optString("status"), description=o.optString("description"),
        photos=buildList { val p=o.optJSONArray("photos") ?: JSONArray(); for(i in 0 until p.length()) add(p.optString(i)) }
    )

    private fun customerToJson(c: Customer) = JSONObject().apply {
        put("id", c.id); put("name", c.name); put("phone", c.phone); put("budget", c.budget)
        put("interest", c.interest); put("notes", c.notes)
    }

    private fun customerFromJson(o: JSONObject) = Customer(
        id=o.optString("id"), name=o.optString("name"), phone=o.optString("phone"),
        budget=o.optString("budget"), interest=o.optString("interest"), notes=o.optString("notes")
    )
}

enum class Screen { DASHBOARD, INVENTORY, ADD_TRUCK, CUSTOMERS, ADD_CUSTOMER, SETTINGS, PUBLIC }
enum class Mode { DEALER, CUSTOMER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TruckShowroomApp() }
    }
}

@Composable
fun TruckShowroomApp() {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val scope = rememberCoroutineScope()
    var trucks by remember { mutableStateOf<List<Truck>>(emptyList()) }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var screen by remember { mutableStateOf(Screen.DASHBOARD) }
    var mode by remember { mutableStateOf(Mode.DEALER) }
    var editing by remember { mutableStateOf<Truck?>(null) }

    LaunchedEffect(Unit) {
        trucks = store.loadTrucks()
        customers = store.loadCustomers()
    }

    fun saveTruck(t: Truck) {
        trucks = (trucks.filterNot { it.id == t.id } + t).sortedByDescending { it.id }
        scope.launch { store.saveTrucks(trucks) }
        screen = Screen.INVENTORY
    }

    fun deleteTruck(t: Truck) {
        trucks = trucks.filterNot { it.id == t.id }
        scope.launch { store.saveTrucks(trucks) }
    }

    fun saveCustomer(c: Customer) {
        customers = (customers.filterNot { it.id == c.id } + c)
        scope.launch { store.saveCustomers(customers) }
        screen = Screen.CUSTOMERS
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFFB42318),
        secondary = Color(0xFF344054),
        background = Color(0xFFF6F7F9),
        surface = Color.White
    )) {
        Scaffold(
            bottomBar = {
                if (mode == Mode.DEALER) BottomBar(screen) {
                    screen = it
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad).background(Color(0xFFF6F7F9))) {
                when (screen) {
                    Screen.DASHBOARD -> Dashboard(trucks, customers) { screen = it }
                    Screen.INVENTORY -> Inventory(trucks, onAdd={editing=null; screen=Screen.ADD_TRUCK},
                        onEdit={editing=it; screen=Screen.ADD_TRUCK}, onDelete=::deleteTruck,
                        onPublic={mode=Mode.CUSTOMER; screen=Screen.PUBLIC})
                    Screen.ADD_TRUCK -> TruckForm(editing, onSave=::saveTruck, onCancel={screen=Screen.INVENTORY})
                    Screen.CUSTOMERS -> Customers(customers, onAdd={screen=Screen.ADD_CUSTOMER})
                    Screen.ADD_CUSTOMER -> CustomerForm(onSave=::saveCustomer, onCancel={screen=Screen.CUSTOMERS})
                    Screen.SETTINGS -> Settings(onCustomerMode={mode=Mode.CUSTOMER; screen=Screen.PUBLIC})
                    Screen.PUBLIC -> PublicCatalog(trucks, onBack={mode=Mode.DEALER; screen=Screen.DASHBOARD})
                }
            }
        }
    }
}

@Composable
fun BottomBar(screen: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar {
        NavigationBarItem(screen==Screen.DASHBOARD, { onSelect(Screen.DASHBOARD) }, icon={Text("⌂")}, label={Text("خانه")})
        NavigationBarItem(screen==Screen.INVENTORY || screen==Screen.ADD_TRUCK, { onSelect(Screen.INVENTORY) }, icon={Text("🚛")}, label={Text("موجودی")})
        NavigationBarItem(screen==Screen.CUSTOMERS || screen==Screen.ADD_CUSTOMER, { onSelect(Screen.CUSTOMERS) }, icon={Text("👥")}, label={Text("مشتریان")})
        NavigationBarItem(screen==Screen.SETTINGS, { onSelect(Screen.SETTINGS) }, icon={Text("⚙")}, label={Text("تنظیمات")})
    }
}

@Composable
fun Dashboard(trucks: List<Truck>, customers: List<Customer>, go: (Screen)->Unit) {
    val available = trucks.count { it.status == "موجود" }
    val sold = trucks.count { it.status == "فروخته‌شده" }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("نمایشگاه‌یار سنگین", fontSize=28.sp, fontWeight=FontWeight.Bold)
        Text("مدیریت هوشمند نمایشگاه خودروهای سنگین", color=Color.Gray)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            StatCard("موجودی", available.toString(), Modifier.weight(1f))
            StatCard("فروخته‌شده", sold.toString(), Modifier.weight(1f))
            StatCard("مشتری", customers.size.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        ActionCard("🚛", "مدیریت خودروها", "ثبت، ویرایش، جستجو و فروش", {go(Screen.INVENTORY)})
        ActionCard("👥", "مدیریت مشتریان", "تماس‌ها و یادداشت‌های مشتری", {go(Screen.CUSTOMERS)})
        ActionCard("📱", "نمایش برای مشتری", "کاتالوگ تمیز برای ارائه خودروها", {go(Screen.PUBLIC)})
    }
}

@Composable
fun StatCard(title:String, value:String, modifier:Modifier) {
    Card(modifier, shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), horizontalAlignment=Alignment.CenterHorizontally) {
            Text(value, fontSize=25.sp, fontWeight=FontWeight.Bold)
            Text(title, fontSize=12.sp, color=Color.Gray)
        }
    }
}

@Composable
fun ActionCard(icon:String, title:String, sub:String, click:()->Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical=6.dp).clickable { click() }, shape=RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment=Alignment.CenterVertically) {
            Text(icon, fontSize=30.sp)
            Spacer(Modifier.width(16.dp))
            Column { Text(title, fontWeight=FontWeight.Bold, fontSize=17.sp); Text(sub, color=Color.Gray, fontSize=13.sp) }
        }
    }
}

@Composable
fun Inventory(trucks:List<Truck>, onAdd:()->Unit, onEdit:(Truck)->Unit, onDelete:(Truck)->Unit, onPublic:()->Unit) {
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("همه") }
    val filtered = trucks.filter {
        (q.isBlank() || "${it.brand} ${it.model} ${it.type} ${it.year}".contains(q, true)) &&
        (filter=="همه" || it.status==filter)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Text("موجودی خودروها", fontSize=25.sp, fontWeight=FontWeight.Bold)
            Button(onClick=onAdd) { Text("+ خودرو") }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(q, {q=it}, Modifier.fillMaxWidth(), label={Text("جستجو برند، مدل، نوع...")}, singleLine=true)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical=8.dp)) {
            listOf("همه","موجود","رزرو","فروخته‌شده").forEach { s ->
                FilterChip(selected=filter==s, onClick={filter=s}, label={Text(s)}, modifier=Modifier.padding(end=6.dp))
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=onPublic) { Text("حالت مشتری") }
        }
        Spacer(Modifier.height(8.dp))
        if(filtered.isEmpty()) EmptyState("هنوز خودرویی ثبت نشده است.") else LazyColumn {
            items(filtered, key={it.id}) { t -> TruckCard(t, onEdit, onDelete) }
        }
    }
}

@Composable
fun TruckCard(t:Truck, onEdit:(Truck)->Unit, onDelete:(Truck)->Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical=6.dp), shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Column {
                    Text("${t.brand} ${t.model}".trim().ifBlank {"خودروی سنگین"}, fontSize=19.sp, fontWeight=FontWeight.Bold)
                    Text("${t.type} • ${t.year} • ${t.mileage} km", color=Color.Gray, fontSize=13.sp)
                }
                StatusPill(t.status)
            }
            Spacer(Modifier.height(8.dp))
            Text(if(t.price.isBlank()) "قیمت: توافقی" else "قیمت: ${t.price}")
            if(t.description.isNotBlank()) Text(t.description, color=Color.Gray, maxLines=2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.End) {
                TextButton(onClick={onEdit(t)}) { Text("ویرایش") }
                TextButton(onClick={if(confirm) onDelete(t) else confirm=true}) { Text(if(confirm) "حذف قطعی" else "حذف") }
            }
        }
    }
}

@Composable
fun StatusPill(status:String) {
    Surface(shape=RoundedCornerShape(50), color=Color(0xFFF2F4F7)) {
        Text(status, Modifier.padding(horizontal=10.dp, vertical=5.dp), fontSize=11.sp)
    }
}

@Composable
fun TruckForm(existing:Truck?, onSave:(Truck)->Unit, onCancel:()->Unit) {
    var t by remember(existing) { mutableStateOf(existing ?: Truck()) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        t = t.copy(photos = (t.photos + uris.map(Uri::toString)).distinct())
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(if(existing==null) "ثبت خودروی جدید" else "ویرایش خودرو", fontSize=25.sp, fontWeight=FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item { Field("شماره موجودی", t.stockNo){t=t.copy(stockNo=it)} }
            item { Field("نوع خودرو", t.type){t=t.copy(type=it)} }
            item { Field("برند", t.brand){t=t.copy(brand=it)} }
            item { Field("مدل", t.model){t=t.copy(model=it)} }
            item { Field("سال", t.year){t=t.copy(year=it)} }
            item { Field("کارکرد", t.mileage){t=t.copy(mileage=it)} }
            item { Field("موتور", t.engine){t=t.copy(engine=it)} }
            item { Field("گیربکس", t.transmission){t=t.copy(transmission=it)} }
            item { Field("محور", t.axle){t=t.copy(axle=it)} }
            item { Field("کابین", t.cabin){t=t.copy(cabin=it)} }
            item { Field("سوخت", t.fuel){t=t.copy(fuel=it)} }
            item { Field("قیمت", t.price){t=t.copy(price=it)} }
            item {
                Text("وضعیت", fontWeight=FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("موجود","رزرو","فروخته‌شده").forEach { s ->
                        FilterChip(t.status==s,{t=t.copy(status=s)},label={Text(s)},modifier=Modifier.padding(end=6.dp))
                    }
                }
            }
            item { Field("توضیحات", t.description){t=t.copy(description=it)}, minLines=3 }
            item {
                OutlinedButton(onClick={picker.launch("image/*")}, Modifier.fillMaxWidth()) {
                    Text("افزودن تصاویر (${t.photos.size})")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick=onCancel, Modifier.weight(1f)) { Text("انصراف") }
            Button(onClick={onSave(t)}, Modifier.weight(1f)) { Text("ذخیره") }
        }
    }
}

@Composable
fun Field(label:String, value:String, onChange:(String)->Unit, minLines:Int=1) {
    OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),label={Text(label)},minLines=minLines)
}

@Composable
fun Customers(customers:List<Customer>, onAdd:()->Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Text("مشتریان", fontSize=25.sp, fontWeight=FontWeight.Bold)
            Button(onClick=onAdd){Text("+ مشتری")}
        }
        Spacer(Modifier.height(10.dp))
        if(customers.isEmpty()) EmptyState("هنوز مشتری ثبت نشده است.") else LazyColumn {
            items(customers, key={it.id}) { c ->
                Card(Modifier.fillMaxWidth().padding(vertical=6.dp), shape=RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(c.name.ifBlank{"بدون نام"}, fontWeight=FontWeight.Bold, fontSize=18.sp)
                        Text(c.phone, color=Color.Gray)
                        if(c.interest.isNotBlank()) Text("علاقه‌مند به: ${c.interest}")
                        if(c.budget.isNotBlank()) Text("بودجه: ${c.budget}")
                        if(c.notes.isNotBlank()) Text(c.notes, color=Color.Gray)
                        if(c.phone.isNotBlank()) {
                            TextButton(onClick={
                                val i=Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}")); LocalContext.current.startActivity(i)
                            }) { Text("تماس") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerForm(onSave:(Customer)->Unit, onCancel:()->Unit) {
    var c by remember { mutableStateOf(Customer()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ثبت مشتری", fontSize=25.sp, fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item { Field("نام و نام خانوادگی",c.name){c=c.copy(name=it)} }
            item { Field("شماره تماس",c.phone){c=c.copy(phone=it)} }
            item { Field("بودجه",c.budget){c=c.copy(budget=it)} }
            item { Field("خودروی موردنظر",c.interest){c=c.copy(interest=it)} }
            item { Field("یادداشت",c.notes){c=c.copy(notes=it)}, minLines=4 }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick=onCancel, Modifier.weight(1f)){Text("انصراف")}
            Button(onClick={onSave(c)}, Modifier.weight(1f)){Text("ذخیره")}
        }
    }
}

@Composable
fun PublicCatalog(trucks:List<Truck>, onBack:()->Unit) {
    var q by remember { mutableStateOf("") }
    val context = LocalContext.current
    val list=trucks.filter{it.status!="فروخته‌شده" && "${it.brand} ${it.model} ${it.type}".contains(q,true)}
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Column { Text("کاتالوگ نمایشگاه",fontSize=25.sp,fontWeight=FontWeight.Bold); Text("خودروهای موجود و رزرو",color=Color.Gray) }
            TextButton(onClick=onBack){Text("بازگشت")}
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(q,{q=it},Modifier.fillMaxWidth(),label={Text("جستجو")},singleLine=true)
        LazyColumn {
            items(list,key={it.id}) { t ->
                Card(Modifier.fillMaxWidth().padding(vertical=7.dp),shape=RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${t.brand} ${t.model}",fontSize=20.sp,fontWeight=FontWeight.Bold)
                        Text("${t.type} • ${t.year} • ${t.mileage} km",color=Color.Gray)
                        Spacer(Modifier.height(6.dp))
                        Text(if(t.price.isBlank()) "قیمت توافقی" else t.price)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            if(t.phoneForDemo().isNotBlank()) {
                                Button(onClick={context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:${t.phoneForDemo()}")))}){Text("تماس")}
                            }
                            OutlinedButton(onClick={
                                val msg="مشخصات ${t.brand} ${t.model} - ${t.type} - سال ${t.year} - ${t.price}"
                                val i=Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")).apply{putExtra("sms_body",msg)}
                                context.startActivity(i)
                            }){Text("ارسال مشخصات")}
                        }
                    }
                }
            }
        }
    }
}

private fun Truck.phoneForDemo(): String = ""

@Composable
fun Settings(onCustomerMode:()->Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("تنظیمات",fontSize=25.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        ActionCard("📱","حالت نمایش مشتری","کاتالوگ خودروها برای ارائه به خریدار",onCustomerMode)
        ActionCard("☁️","ذخیره‌سازی ابری","ساختار CloudSync آماده اتصال به Firebase/API در نسخه بعدی",{})
        ActionCard("🏪","اطلاعات نمایشگاه","نام، تلفن، آدرس و لوگو در نسخه بعدی",{})
    }
}

@Composable
fun EmptyState(text:String) {
    Box(Modifier.fillMaxWidth().padding(40.dp),contentAlignment=Alignment.Center) {
        Text(text,color=Color.Gray,textAlign=TextAlign.Center)
    }
}
