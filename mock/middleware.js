// Middleware to handle custom routing for path parameters
// Maps requests like /products/1 to /products?product_id=1

const routes = require('./routes.json');

module.exports = (req, res, next) => {
  // Check if the current path matches any route pattern
  let foundMatch = false;

  for (const [pattern, target] of Object.entries(routes)) {
    // Convert pattern like /products/:product_id to regex
    const patternRegex = new RegExp('^' + pattern.replace(/:[a-zA-Z_]+/g, '([^/]+)') + '$');
    const paramNames = (pattern.match(/:[a-zA-Z_]+/g) || []).map(p => p.substring(1));

    const match = req.path.match(patternRegex);

    if (match) {
      foundMatch = true;
      let newPath = target;

      // Replace :paramName with actual values
      paramNames.forEach((paramName, index) => {
        newPath = newPath.replace(':' + paramName, match[index + 1]);
      });

      // Redirect the request to the new path
      req.url = newPath + (req.query && Object.keys(req.query).length > 0
        ? '?' + new URLSearchParams(req.query).toString()
        : '');
      req.path = newPath;

      break;
    }
  }

  next();
};
