const express = require('express');
const router = express.Router();

router.get('/', function(req, res) {
   res.render('index', {title: "Major Project"});
});

module.exports = router;