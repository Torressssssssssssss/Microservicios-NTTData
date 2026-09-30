// Migracion de laboratorio; no sobrescribe snapshots de ordenes historicas.
db.ingredient.updateMany({unitPrice: {$exists:false}}, {$set:{unitPrice:NumberDecimal("1.00"),available:true,stockOnHand:100,reorderLevel:10,version:NumberLong(0),dietaryTags:[],allergens:[],spiceLevel:0}});
db.taco.updateMany({published:{$exists:false}}, {$set:{published:true,dietaryTags:[],allergens:[],spiceLevel:0}});
db.tacoOrder.updateMany({status:{$exists:false}}, {$set:{status:"CREATED"}});
db.tacoOrder.updateMany({version:{$exists:false}}, {$set:{version:NumberLong(0)}});
