@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.riposte.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import dev.riposte.core.*

fun ratingNumber(value: Double) = value.toString().removeSuffix(".0")
val preparationKeys = listOf("willingness", "warmup", "transport")
@Composable fun DefinitionInput(d: TrackerRevision,value: String,onValue: (String)->Unit) {
    when(d.kind) {
        "Rating" -> FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { d.levels.forEach { level ->
            val n=level.value.toString()
            FilterChip(selected=value.toDoubleOrNull()==level.value,onClick={onValue(n)},label={Text(if(level.label==ratingNumber(level.value)) level.label else ratingNumber(level.value)+" · "+level.label)})
        } }
        "Tag" -> { Chips(d.choices,value,onValue) }
        else -> Input(d.name,value,onValue,true)
    }
}
@Composable fun DefinitionForm(initial: TrackerRevision,onClose: ()->Unit,onSave: (TrackerRevision)->Unit) {
    var name by remember {mutableStateOf(initial.name)}; var unit by remember {mutableStateOf(initial.unit)}
    var low by remember {mutableStateOf(initial.min.toString())}; var high by remember {mutableStateOf(initial.max.toString())}
    var levels by remember {mutableStateOf(initial.levels.joinToString("\n") { ratingNumber(it.value)+" = "+it.label })}
    var choices by remember {mutableStateOf(initial.choices.joinToString("\n"))}; var error by remember {mutableStateOf("")}
    Form("Edit tracker",onClose,{try {
        val parsed=if(initial.kind=="Rating") levels.lines().filter {it.isNotBlank()}.map { line ->
            val parts=line.split('=',limit=2);require(parts.size==2) {"Use value = label for each level."};RatingLevel(parts[0].trim().toDouble(),parts[1].trim())
        } else emptyList()
        val d=initial.copy(name=name.trim(),unit=unit.trim(),min=if(parsed.isNotEmpty()) parsed.first().value else low.toDouble(),max=if(parsed.isNotEmpty()) parsed.last().value else high.toDouble(),levels=parsed,choices=choices.lines().map {it.trim()}.filter {it.isNotBlank()})
        d.validate();onSave(d)
    } catch(e: Exception) {error=e.message ?: "Check the definition."}}) {
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        Input("Name",name,{name=it});Hint("Type: "+initial.kind+". Saved entries retain their original scale.")
        if(initial.kind=="Rating") {Input("Levels · one value = label per line",levels,{levels=it},lines=8);Hint("Add or remove lines to change the number of levels. Values must increase.")}
        if(initial.kind in listOf("Number","Duration","Counter")) {Input("Unit",unit,{unit=it});Input("Minimum",low,{low=it},true);Input("Maximum",high,{high=it},true)}
        if(initial.kind=="Tag") Input("Choices · one per line",choices,{choices=it},lines=6)
    }
}
@Composable fun LensesInput(value: Boolean?,onChange: (Boolean?)->Unit) {
    Text("Contact lenses");Chips(listOf("Yes","No"),when(value){true->"Yes";false->"No";null->""},{onChange(it=="Yes")})
    TextButton(onClick={onChange(null)}) {Text("Leave unknown")}
}
@Composable fun EquipmentFields(categories: List<EquipmentCategory>,values: Map<String,String>,onChange: (Map<String,String>)->Unit) {
    categories.filter { !it.archived || it.id in values }.forEach { c -> Input(c.name+if(c.archived) " (archived)" else "",values[c.id].orEmpty(),{v->onChange(if(v.isBlank()) values-c.id else values+(c.id to v))}) }
    values.keys.filter {id->categories.none {it.id==id}}.forEach {id->Input("Equipment "+id,values[id].orEmpty(),{onChange(values+(id to it))})}
}
@Composable fun CategoryForm(initial: List<EquipmentCategory>,onClose: ()->Unit,onSave: (List<EquipmentCategory>)->Unit) {
    var categories by remember {mutableStateOf(initial)};var name by remember {mutableStateOf("")};var error by remember {mutableStateOf("")}
    Form("Equipment categories",onClose,{if(categories.any {it.name.isBlank()} || categories.map {it.name.trim().lowercase()}.distinct().size!=categories.size) error="Use unique, nonblank category names." else onSave(categories.map {it.copy(name=it.name.trim())})}) {
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        categories.forEachIndexed { i,c ->
            Input("Category name",c.name,{v->categories=categories.map {if(it.id==c.id) it.copy(name=v) else it}})
            Row {
                TextButton(onClick={if(i>0) categories=categories.toMutableList().apply {java.util.Collections.swap(this,i,i-1)}}) {Text("↑")}
                TextButton(onClick={if(i<categories.lastIndex) categories=categories.toMutableList().apply {java.util.Collections.swap(this,i,i+1)}}) {Text("↓")}
                TextButton(onClick={categories=categories.map {if(it.id==c.id) it.copy(archived=!it.archived) else it}}) {Text(if(c.archived) "Restore" else "Archive")}
            }
        }
        Input("New category",name,{name=it});TextButton(onClick={if(name.isNotBlank()){categories=categories+EquipmentCategory(name=name.trim());name=""}}) {Text("Add category")}
        Hint("Archiving hides a category from new entries and keeps saved equipment.")
    }
}
