import mongoose from "mongoose";
import validator from "validator";

const User = new mongoose.Schema({
    firstName: {
        type: String,
        required: true
    },
    lastName: String,
    mobileNo: {
        type: Number,
        required: true,
        validate: {
            validator: function(mobileNo){
                if(mobileNo<1000000000 && mobileNo>9999999999)
                    console.log(mobileNo); 
                    throw new Error("please provide valid phone no");
            }
        },
        
        unique: true,
    },
    address: String,
    email: {
        type: String,
        validate: {
            validator: function(email){
                console.log(email)
                if(!validator.isEmail(email)) throw new Error("please provide valid email");
            }
        }
    }
});

export const user = mongoose.model('user', User);