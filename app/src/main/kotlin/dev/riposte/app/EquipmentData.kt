package dev.riposte.app

import dev.riposte.core.*
import dev.riposte.fencing.*

fun Snapshot.migrateEquipment(): Snapshot {
    val profiles=equipmentProfiles.toMutableList()
    fun resolve(owner: String,kind: String,name: String,id: String): String {
        if(id.isNotBlank() && profiles.any {it.id==id}) return id
        if(name.isBlank()) return ""
        return profiles.find {it.owner==owner && it.kind==kind && (listOf(it.name)+it.aliases).any {n->normalizedName(n)==normalizedName(name)}}?.id
            ?: EquipmentProfile(owner=owner,kind=kind,name=name).also {profiles.add(it)}.id
    }
    fun details(owner: String,d: ParticipantDetails)=d.copy(swordId=resolve(owner,"Foil",d.sword,d.swordId),gloveId=resolve(owner,"Glove",d.glove,d.gloveId))
    val sessions=sessions.map {it.copy(swordId=resolve(me,"Foil",it.sword,it.swordId),gloveId=resolve(me,"Glove",it.glove,it.gloveId))}
    val bouts=bouts.map {it.copy(detailsA=details(it.a,it.detailsA),detailsB=details(it.b,it.detailsB))}
    return copy(equipmentProfiles=profiles,sessions=sessions,bouts=bouts)
}

fun Snapshot.mergeEquipment(from: String,to: String): Snapshot {
    require(from!=to)
    val source=equipmentProfiles.single {it.id==from};val target=equipmentProfiles.single {it.id==to}
    require(source.owner==target.owner && source.kind==target.kind)
    val merged=target.copy(aliases=(target.aliases+source.aliases+source.name).distinct())
    fun details(d: ParticipantDetails)=d.copy(swordId=if(d.swordId==from) to else d.swordId,gloveId=if(d.gloveId==from) to else d.gloveId)
    return copy(equipmentProfiles=equipmentProfiles.filterNot {it.id==from}.map {if(it.id==to) merged else it},
        sessions=sessions.map {it.copy(swordId=if(it.swordId==from) to else it.swordId,gloveId=if(it.gloveId==from) to else it.gloveId)},
        bouts=bouts.map {it.copy(detailsA=details(it.detailsA),detailsB=details(it.detailsB))})
}

fun equipmentValue(s: Snapshot,d: ParticipantDetails,field: String): String {
    val foil=s.equipmentProfiles.find {it.id==d.swordId};val glove=s.equipmentProfiles.find {it.id==d.gloveId}
    return when(field) {"Foil"->foil?.name ?: d.sword;"Glove"->glove?.name ?: d.glove;"Blade type"->foil?.blade;"Handle type"->foil?.handle;"Set angle"->foil?.angle
        "Glove model"->glove?.brand;"Glove size"->glove?.size
        else->when {field.startsWith("Foil: ")->foil?.properties?.get(field.removePrefix("Foil: "));field.startsWith("Glove: ")->glove?.properties?.get(field.removePrefix("Glove: "));else->d.equipment[field]}}.orEmpty().ifBlank {"Unknown"}
}
fun equipmentFields(s: Snapshot)=listOf("Foil","Glove","Blade type","Handle type","Set angle","Glove model","Glove size")+
    s.equipmentProfiles.flatMap {profile->profile.properties.keys.map {"${profile.kind}: $it"}}.distinct()+s.settings.equipmentCategories.map {it.id}
