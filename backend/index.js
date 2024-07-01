import "mongodb";
import express from "express";

const port = process.env.BACKEND_PORT || 3000;
const app = express();

app.get('/', (req, res) => {
    res.send('Hello World!')
})

app.listen(port,()=>{
    console.log(`application is running on ${port}`);
})
