// Custom JSON Server with middleware support for routes.json
const jsonServer = require('json-server');
const fs = require('fs');
const path = require('path');
const middleware = require('./middleware');

// Create express app
const app = jsonServer.create();

// Add custom middleware for routing
app.use(jsonServer.bodyParser);
app.use(middleware);

// Create router
const dbPath = path.join(__dirname, 'db.json');
const db = JSON.parse(fs.readFileSync(dbPath, 'utf-8'));
const router = jsonServer.router(db);

// Use default middlewares (logger, static, etc)
app.use(jsonServer.defaults());

// Use router
app.use(router);

// Start server
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || 'localhost';

app.listen(PORT, HOST, () => {
  console.log(`JSON Server is running on http://${HOST}:${PORT}`);
  console.log(`Database: ${dbPath}`);
  console.log('Routes loaded from routes.json');
});
