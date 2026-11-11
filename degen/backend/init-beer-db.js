const { MongoClient } = require('mongoose').mongo;
(async () => {
  const direct = new MongoClient('mongodb://127.0.0.1:27028/?directConnection=true', { serverSelectionTimeoutMS: 3000 });
  try {
    const admin = direct.db('admin');
    try {
      await admin.command({ replSetInitiate: { _id: 'beer', members: [{ _id: 0, host: '127.0.0.1:27028' }] } });
    } catch (err) {
      if (err.codeName !== 'AlreadyInitialized') throw err;
    }
  } finally {
    await direct.close();
  }
  const replica = new MongoClient('mongodb://127.0.0.1:27028/?replicaSet=beer', { serverSelectionTimeoutMS: 15000 });
  try {
    await replica.db('admin').command({ ping: 1 });
    console.log('MongoDB replica set ready.');
  } finally {
    await replica.close();
  }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
