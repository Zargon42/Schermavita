package dev.riposte.app

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val shortDate = DateTimeFormatter.ofPattern("MMM d")
private val earliest = LocalDate.of(1900, 1, 1)

@Composable private fun HabitDate(label: String, value: LocalDate, min: LocalDate, max: LocalDate, change: (LocalDate)->Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        DatePickerDialog(context, { _, y, m, d -> change(LocalDate.of(y,m+1,d)) },
            value.year, value.monthValue-1, value.dayOfMonth).apply {
            val zone = ZoneId.systemDefault()
            datePicker.minDate = min.atStartOfDay(zone).toInstant().toEpochMilli()
            datePicker.maxDate = max.atStartOfDay(zone).toInstant().toEpochMilli()
        }.show()
    }) { Text("$label · $value") }
}

private fun HabitStats.window(goal: HabitGoal) = if (goal.countdown) "Due by ${through.format(shortDate)}"
    else if (from == through) from.format(shortDate) else "${from.format(shortDate)} – ${through.format(shortDate)}"

@Composable fun HabitsScreen(repo: AppRepository, s: Snapshot, today: LocalDate, run: (suspend ()->Unit)->Unit) {
    var selected by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var logging by rememberSaveable { mutableStateOf("") }
    var entryId by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }
    val habit = s.habits.find { it.id == selected }
    val grouped = remember(s.habitEntries) { s.habitEntries.groupBy { it.habitId } }
    BackHandler(habit != null && !editing && logging.isEmpty()) { selected="" }

    fun openLog(h: Habit, e: HabitEntry? = null) { logging=h.id; entryId=e?.id.orEmpty() }
    fun saveEntry(entry: HabitEntry) = run {
        repo.change { it.copy(habitEntries=it.habitEntries.replace(entry) { id }) }
        logging=""
    }
    fun removeEntry(id: String) = run {
        repo.change { s0 -> s0.copy(habitEntries=s0.habitEntries.filterNot { it.id==id }) }
        logging=""
    }

    if (habit == null) {
        val visible = s.habits.filter { it.archived==archived && it.start<=today }
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(if (archived) "Archived habits" else "Habits",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                    if (!archived) FilledTonalButton(onClick={editing=true}) { Text("Add habit") }
                }
            }
            if (visible.isEmpty()) item { Hint(if (archived) "No archived habits." else "Add your first habit to start tracking.") }
            items(visible,key={it.id}) { h ->
                val entries=grouped[h.id].orEmpty()
                val stats=remember(h,entries,today) { h.stats(entries,today) }
                val goal=h.goalOn(today)
                val logged=entries.lastOrNull { it.day==today.toString() }
                ListItem(headlineContent={Text(h.name,fontWeight=FontWeight.SemiBold)},
                    supportingContent={Column { Text(goal.label()); Hint("${stats.window(goal)} · ${stats.progress.stripTrailingZeros().toPlainString()}/${habitNumber(goal.target)}") }},
                    modifier=Modifier.clickable { selected=h.id },
                    trailingContent={ if (!archived) TextButton(onClick={
                        when {
                            goal.type!=HabitType.CheckIns -> openLog(h)
                            goal.binary && logged!=null -> removeEntry(logged.id)
                            else -> saveEntry(HabitEntry(h.id,today.toString()))
                        }
                    }) { Text(when {goal.type!=HabitType.CheckIns -> "Log"; goal.binary && logged!=null -> "Undo"; goal.binary -> "Check in"; else -> "+1"}) } })
                HorizontalDivider()
            }
            if (archived || s.habits.any { it.archived }) item {
                TextButton(onClick={archived=!archived}) { Text(if (archived) "Active habits" else "Archived habits") }
            }
        }
    } else {
        val entries=grouped[habit.id].orEmpty()
        val stats=remember(habit,entries,today) { habit.stats(entries,today) }
        val goal=habit.goalOn(today)
        val streakUnit=if (habit.revisions.size>1 || goal.every!=1 || goal.countdown) "periods" else goal.period.name.lowercase()
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {
                TextButton(onClick={selected=""}) { Text("Back to habits") }
                Heading(habit.name)
                Text(goal.label())
                Hint("${stats.window(goal)} · ${stats.progress.stripTrailingZeros().toPlainString()} of ${habitNumber(goal.target)}")
                habit.revisions.lastOrNull { LocalDate.parse(it.from)>today }?.let { Hint("From ${it.from} · ${it.goal.label()}") }
            }
            item { Panel("Consistency") {
                Text(stats.consistency?.let { "$it%" } ?: "First period in progress",style=MaterialTheme.typography.headlineMedium)
                Text("Current streak · ${stats.current} $streakUnit")
                Text("Best streak · ${stats.best} $streakUnit")
                Hint("Partial goals earn partial credit when a period closes. Recent periods carry more weight, halving every ten periods. A missed goal breaks the streak. A shortened first calendar period counts only if completed.")
            } }
            item {
                Row {
                    if (!habit.archived) TextButton(onClick={editing=true}) {Text("Edit habit")}
                    TextButton(onClick={run {repo.change { state -> state.copy(habits=state.habits.map { if(it.id==habit.id) it.copy(archived=!habit.archived) else it }) };selected="" }}) {
                        Text(if (habit.archived) "Restore habit" else "Archive habit")
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("History",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    if (!habit.archived) TextButton(onClick={openLog(habit)}) {Text("Add entry")}
                }
                if (entries.isEmpty()) Hint("No entries yet.")
            }
            items(entries.asReversed().sortedByDescending {it.day},key={it.id}) { entry ->
                val historical=habit.goalOn(LocalDate.parse(entry.day))
                ListItem(headlineContent={Text(if(historical.type==HabitType.CheckIns) "Check-in" else "${habitNumber(entry.amount)} ${historical.unit}")},
                    supportingContent={Text(entry.day)},modifier=Modifier.clickable(enabled=!habit.archived) {openLog(habit,entry)})
            }
        }
    }
    if (editing) HabitEditor(habit,today,grouped[habit?.id].orEmpty(),{editing=false}) { name,start,goal ->
        run {
            repo.change { state ->
                val old=state.habits.find {it.id==selected}
                val saved=if(old==null) Habit(name,listOf(HabitRevision(start.toString(),goal))) else {
                    val active=old.revisions.filter {LocalDate.parse(it.from)<=today}
                    val effective=old.stats(state.habitEntries,today).nextPeriodStart(today)
                    val revisions=if(active.last().goal==goal) active else active+HabitRevision(effective.toString(),goal)
                    old.copy(name=name,revisions=revisions)
                }
                state.copy(habits=state.habits.replace(saved) {id})
            }
            editing=false
        }
    }
    s.habits.find {it.id==logging}?.let { h ->
        val original=s.habitEntries.find {it.id==entryId}
        HabitLog(h,today,original,{logging=""},::saveEntry,::removeEntry)
    }
}

@Composable private fun HabitEditor(habit: Habit?, today: LocalDate, entries: List<HabitEntry>, close: ()->Unit,
    save: (String,LocalDate,HabitGoal)->Unit) {
    val initial=habit?.revisions?.last()?.goal ?: HabitGoal()
    var name by rememberSaveable { mutableStateOf(habit?.name.orEmpty()) }
    var start by rememberSaveable { mutableStateOf((habit?.start ?: today).toString()) }
    var type by rememberSaveable { mutableStateOf(initial.type) }
    var period by rememberSaveable { mutableStateOf(initial.period) }
    var every by rememberSaveable { mutableStateOf(initial.every.toString()) }
    var target by rememberSaveable { mutableStateOf(habitNumber(initial.target)) }
    var threshold by rememberSaveable { mutableStateOf(habitNumber(initial.threshold)) }
    var unit by rememberSaveable { mutableStateOf(initial.unit) }
    var countdown by rememberSaveable { mutableStateOf(initial.countdown) }
    val canCount=period==HabitPeriod.Days && type!=HabitType.Quantity && target.toDoubleOrNull()==1.0
    val goal=HabitGoal(type,target.toDoubleOrNull() ?: 0.0,every.toIntOrNull() ?: 0,period,
        countdown && canCount && every.toIntOrNull()!=1,if(type==HabitType.CheckIns) "" else unit.trim(),
        if(type==HabitType.Sessions) threshold.toDoubleOrNull() ?: 0.0 else 1.0)
    val valid=runCatching {goal.validate()}.isSuccess
    val effective=remember(habit,entries,today,start) { habit?.stats(entries,today)?.nextPeriodStart(today) ?: LocalDate.parse(start) }
    Form(if(habit==null) "New habit" else "Edit habit",close,if(valid && name.isNotBlank()) {{save(name.trim(),LocalDate.parse(start),goal)}} else null) {
        Input("Habit name",name,{name=it})
        val types=mapOf("Check-ins" to HabitType.CheckIns,"Quantity" to HabitType.Quantity,"Qualifying sessions" to HabitType.Sessions)
        Chips(types.keys.toList(),types.entries.first {it.value==type}.key,{type=types.getValue(it)})
        Input(when(type) {HabitType.CheckIns->"Check-ins per period";HabitType.Quantity->"Total per period";HabitType.Sessions->"Sessions per period"},target,{target=it},numeric=true)
        if(type!=HabitType.CheckIns) Input("Unit",unit,{unit=it})
        if(type==HabitType.Sessions) Input("Minimum per session",threshold,{threshold=it},numeric=true)
        Text("Frequency",style=MaterialTheme.typography.titleMedium)
        Chips(HabitPeriod.entries.map {it.name},period.name,{period=HabitPeriod.valueOf(it)})
        Input("Number of ${period.name.lowercase()} in each period",every,{every=it},numeric=true)
        if(canCount && every!="1") Chips(listOf("Fixed blocks","Countdown"),if(countdown) "Countdown" else "Fixed blocks",{countdown=it=="Countdown"})
        if(habit==null) HabitDate("Start date",LocalDate.parse(start),earliest,today) {start=it.toString()}
        if(valid) {
            Text(goal.label(),fontWeight=FontWeight.SemiBold)
            val end=goal.nextBoundary(effective).minusDays(1)
            Hint(if(goal.countdown) "Due by $end. Checking in starts a new countdown." else "Period · $effective – $end")
            if(habit!=null) Hint("Goal changes take effect on $effective.")
        } else Hint("Enter a positive target and a period of 1–999 whole units. Counts use whole numbers. Measured goals need a unit.")
    }
}

@Composable private fun HabitLog(h: Habit,today: LocalDate,entry: HabitEntry?,close: ()->Unit,
    save: (HabitEntry)->Unit,remove: (String)->Unit) {
    var day by rememberSaveable {mutableStateOf(entry?.day ?: today.toString())}
    var input by rememberSaveable {mutableStateOf(entry?.let {habitNumber(it.amount)}.orEmpty())}
    val goal=h.goalOn(LocalDate.parse(day))
    val amount=if(goal.type==HabitType.CheckIns) 1.0 else input.toDoubleOrNull()
    Form(if(entry==null) "Log ${h.name}" else "Edit entry",close,if(amount!=null && amount.isFinite() && amount>0) {{
        save((entry ?: HabitEntry(h.id,day)).copy(day=day,amount=amount))
    }} else null) {
        HabitDate("Tracking day",LocalDate.parse(day),h.start,today) {day=it.toString()}
        Text(goal.label())
        if(goal.type!=HabitType.CheckIns) Input("Amount (${goal.unit})",input,{input=it},numeric=true)
        if(entry!=null) TextButton(onClick={remove(entry.id)}) {Text("Remove entry")}
    }
}

