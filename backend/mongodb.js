import mongoose from "mongoose";
import { user } from "./src/user/userModule.js";
const url = "mongodb://localhost/mydatabase";

try{
    mongoose.connect(url, {
        useNewUrlParser: true,
        useUnifiedTopology: true,
    });
    console.log("user connected to database");
}catch(e){
    console.log(e.message);
}
// const schema = new mongoose.Schema({ name: String, size: String });
// const Tank = mongoose.model('Tank', schema);

const user1 = new user({ 
    firstName: 'sumangalam',
    mobileNo: 8987456767878,
    email: 'shjknjan@mail.com'
 });
console.log(user1);
await user1.validate();
await user1.save();


    