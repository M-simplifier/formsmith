namespace Formsmith

inductive Value where
  | nil
  | bool (value : Bool)
  | object (id : Nat)
  deriving DecidableEq, Repr

def truthy : Value -> Bool
  | Value.nil => false
  | Value.bool false => false
  | Value.bool true => true
  | Value.object _ => true

def cljNot (value : Value) : Value :=
  Value.bool (!truthy value)

def cljIf (test thenBranch elseBranch : Value) : Value :=
  if truthy test then thenBranch else elseBranch

def cljWhen (test body : Value) : Value :=
  cljIf test body Value.nil

def cljWhenNot (test body : Value) : Value :=
  cljIf (cljNot test) body Value.nil

def cljIfNot (test thenBranch elseBranch : Value) : Value :=
  cljIf (cljNot test) thenBranch elseBranch

theorem when_not_over_not_equiv (test body : Value) :
    cljWhen (cljNot test) body = cljWhenNot test body := by
  rfl

theorem if_not_over_not_equiv (test thenBranch elseBranch : Value) :
    cljIf (cljNot test) thenBranch elseBranch =
      cljIfNot test thenBranch elseBranch := by
  rfl

end Formsmith
