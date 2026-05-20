package ph.nextbank.drums.data.db

import androidx.room.TypeConverter
import ph.nextbank.drums.data.model.ImportSource

class Converters {
    @TypeConverter fun fromImportSource(v: ImportSource): String = v.name
    @TypeConverter fun toImportSource(v: String): ImportSource = ImportSource.valueOf(v)
}
