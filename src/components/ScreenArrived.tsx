"use client";

import React from "react";
import { motion } from "framer-motion";
import { MapPin } from "lucide-react";
import { MassiveButton } from "./MassiveButton";

interface ScreenArrivedProps {
  onDismiss: () => void;
}

export function ScreenArrived({ onDismiss }: ScreenArrivedProps) {
  return (
    <motion.div 
      className="flex h-full w-full flex-col items-center justify-center py-6 gap-6 relative"
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ duration: 0.3 }}
    >
      <div className="flex flex-col items-center gap-2">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary text-white mb-2">
          <MapPin size={32} strokeWidth={2.5} />
        </div>
        <h1 className="text-center text-[24px] font-bold leading-tight tracking-tight text-black">
          ¡Has llegado!
        </h1>
        <p className="text-neutral-500 text-[14px] font-semibold text-center">
          Centro Histórico
        </p>
      </div>

      <MassiveButton 
        variant="primary" 
        onClick={onDismiss}
        className="text-[13px] tracking-widest mt-4"
      >
        ACEPTAR
      </MassiveButton>
    </motion.div>
  );
}
