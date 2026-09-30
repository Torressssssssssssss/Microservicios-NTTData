// Ejecutar con mongosh sobre la base de laboratorio antes de iniciar la nueva version.
const fields = {ccNumber: "", ccExpiration: "", ccCVV: ""};
for (const name of ["tacoOrder", "paymentMethod"]) {
  const collection = db.getCollection(name);
  print(name + ": " + collection.countDocuments({$or: Object.keys(fields).map(key => ({[key]: {$exists: true}}))}) + " documentos heredados");
  collection.updateMany({}, {$unset: fields});
}
// Los metodos heredados sin token no pueden reutilizarse: volver a tokenizar.
db.paymentMethod.deleteMany({paymentToken: {$exists: false}});
